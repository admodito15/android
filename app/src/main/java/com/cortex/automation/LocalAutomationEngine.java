package com.cortex.automation;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class LocalAutomationEngine {
    interface Listener {
        void onChanged(List<AutomationModels.AutomationTask> tasks, AutomationModels.TaskStats stats, String event);
    }

    private static final String PREFS = "cortex_local_automation";
    private static final String KEY_TASKS = "tasks";
    private static final int MAX_RESULT_CHARS = 5000;

    private final SharedPreferences preferences;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Object lock = new Object();
    private final List<AutomationModels.AutomationTask> tasks = new ArrayList<>();

    private Listener listener;
    private boolean paused;
    private boolean workerActive;

    LocalAutomationEngine(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        tasks.addAll(AutomationModels.fromJsonArray(preferences.getString(KEY_TASKS, "[]")));
        resetRunningTasks();
    }

    void setListener(Listener listener) {
        this.listener = listener;
        notifyChanged("Advanced native automation engine ready");
        pump();
    }

    void addTask(AutomationModels.AutomationTask task) {
        synchronized (lock) {
            tasks.add(0, task);
            persistLocked();
        }
        notifyChanged(task.scheduledAt > System.currentTimeMillis() ? "Scheduled " + task.title : "Queued " + task.title);
        pump();
    }

    void duplicateLatest() {
        synchronized (lock) {
            if (tasks.isEmpty()) {
                notifyChanged("No task available to duplicate");
                return;
            }
            AutomationModels.AutomationTask copy = tasks.get(0).duplicate();
            tasks.add(0, copy);
            persistLocked();
        }
        notifyChanged("Duplicated latest task");
        pump();
    }

    void pause() {
        paused = true;
        notifyChanged("Queue paused");
    }

    void resume() {
        paused = false;
        notifyChanged("Queue resumed");
        pump();
    }

    void retryFailed() {
        long now = System.currentTimeMillis();
        synchronized (lock) {
            for (int i = 0; i < tasks.size(); i++) {
                AutomationModels.AutomationTask task = tasks.get(i);
                if (AutomationModels.STATUS_FAILED.equals(task.status)) {
                    tasks.set(i, task.withStatus(AutomationModels.STATUS_QUEUED, task.result, "", task.attempts, now));
                }
            }
            persistLocked();
        }
        notifyChanged("Failed tasks moved back to queue");
        pump();
    }

    void clearFinished() {
        synchronized (lock) {
            for (int i = tasks.size() - 1; i >= 0; i--) {
                String status = tasks.get(i).status;
                if (AutomationModels.STATUS_DONE.equals(status) || AutomationModels.STATUS_FAILED.equals(status)) tasks.remove(i);
            }
            persistLocked();
        }
        notifyChanged("Finished tasks cleared");
    }

    void clearAll() {
        synchronized (lock) {
            tasks.clear();
            persistLocked();
        }
        notifyChanged("All tasks cleared");
    }

    String exportReport() {
        StringBuilder builder = new StringBuilder("Cortex Native Automation Report\n\n");
        synchronized (lock) {
            AutomationModels.TaskStats stats = statsLocked(System.currentTimeMillis());
            builder.append(stats.summary()).append("\n\n");
            for (AutomationModels.AutomationTask task : tasks) {
                builder.append(task.status.toUpperCase(Locale.US)).append(" • ")
                        .append(task.priorityLabel()).append(" • ")
                        .append(task.title).append("\n")
                        .append(task.type).append(" • ").append(task.method).append(" • retries ")
                        .append(task.attempts).append("/").append(task.maxRetries).append(" • timeout ")
                        .append(task.timeoutSeconds).append("s\n");
                if (!task.url.isEmpty()) builder.append(task.url).append("\n");
                if (!task.error.isEmpty()) builder.append("Error: ").append(task.error).append("\n");
                if (!task.result.isEmpty()) builder.append(task.result).append("\n");
                builder.append("\n");
            }
        }
        return builder.toString().trim();
    }

    private void pump() {
        synchronized (lock) {
            if (paused || workerActive) return;
            int index = nextReadyIndexLocked(System.currentTimeMillis());
            if (index < 0) {
                scheduleNextPumpLocked();
                return;
            }
            AutomationModels.AutomationTask task = tasks.get(index);
            tasks.set(index, task.withStatus(AutomationModels.STATUS_RUNNING, task.result, "", task.attempts + 1, task.scheduledAt));
            workerActive = true;
            persistLocked();
            notifyChanged("Running " + task.title);
            executor.execute(() -> runTask(task.id));
        }
    }

    private void runTask(String taskId) {
        AutomationModels.AutomationTask runningTask = findTask(taskId);
        if (runningTask == null) {
            markWorkerDone();
            return;
        }
        try {
            String result = executeHttp(runningTask);
            updateTask(taskId, AutomationModels.STATUS_DONE, result, "", runningTask.scheduledAt);
            notifyChanged("Completed " + runningTask.title);
        } catch (Exception error) {
            handleFailure(runningTask, error);
        } finally {
            markWorkerDone();
            pump();
        }
    }

    private void handleFailure(AutomationModels.AutomationTask task, Exception error) {
        String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        if (task.attempts <= task.maxRetries) {
            long retryAt = System.currentTimeMillis() + retryDelayMillis(task.attempts);
            updateTask(task.id, AutomationModels.STATUS_QUEUED, task.result, "Retry scheduled: " + message, retryAt);
            notifyChanged("Retry scheduled for " + task.title);
        } else {
            updateTask(task.id, AutomationModels.STATUS_FAILED, "", message, task.scheduledAt);
            notifyChanged("Failed " + task.title + ": " + message);
        }
    }

    private String executeHttp(AutomationModels.AutomationTask task) throws Exception {
        if (task.url == null || task.url.trim().isEmpty()) {
            return buildLocalPlan(task);
        }
        String cleanedUrl = task.url.trim();
        if (!cleanedUrl.startsWith("http://") && !cleanedUrl.startsWith("https://")) {
            throw new IllegalArgumentException("URL must start with http:// or https://");
        }
        URL endpoint = new URL(cleanedUrl);
        HttpURLConnection connection = (HttpURLConnection) endpoint.openConnection();
        try {
            String method = task.method.toUpperCase(Locale.US);
            int timeoutMillis = Math.max(5, task.timeoutSeconds) * 1000;
            connection.setRequestMethod(method);
            connection.setConnectTimeout(timeoutMillis);
            connection.setReadTimeout(timeoutMillis);
            connection.setRequestProperty("User-Agent", "CortexAutomationAndroid/3.0");
            connection.setRequestProperty("Accept", "application/json,text/plain,*/*");
            applyHeaders(connection, task.headers);
            if (allowsBody(method) && !task.body.trim().isEmpty()) {
                byte[] bytes = task.body.getBytes(StandardCharsets.UTF_8);
                connection.setDoOutput(true);
                if (connection.getRequestProperty("Content-Type") == null) {
                    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                }
                connection.setFixedLengthStreamingMode(bytes.length);
                try (OutputStream outputStream = connection.getOutputStream()) {
                    outputStream.write(bytes);
                }
            }
            int code = connection.getResponseCode();
            String response = readAll(code >= 200 && code < 400 ? connection.getInputStream() : connection.getErrorStream());
            String result = method + " " + cleanedUrl + "\nHTTP " + code + "\n" + response;
            if (code < 200 || code >= 400) throw new IllegalStateException(trim(result));
            return trim(result);
        } finally {
            connection.disconnect();
        }
    }

    private void applyHeaders(HttpURLConnection connection, String rawHeaders) throws Exception {
        if (rawHeaders == null || rawHeaders.trim().isEmpty()) return;
        String trimmed = rawHeaders.trim();
        if (trimmed.startsWith("{")) {
            JSONObject json = new JSONObject(trimmed);
            java.util.Iterator<String> keys = json.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                connection.setRequestProperty(key, json.optString(key));
            }
            return;
        }
        String[] lines = trimmed.split("\\r?\\n");
        for (String line : lines) {
            int separator = line.indexOf(':');
            if (separator > 0) {
                connection.setRequestProperty(line.substring(0, separator).trim(), line.substring(separator + 1).trim());
            }
        }
    }

    private String buildLocalPlan(AutomationModels.AutomationTask task) {
        return "Local automation plan generated on device\n"
                + "Title: " + task.title + "\n"
                + "Type: " + task.type + "\n"
                + "Priority: " + task.priorityLabel() + "\n"
                + "Retries: " + task.maxRetries + " • Timeout: " + task.timeoutSeconds + "s\n\n"
                + "1. Validate required endpoint, credentials, payload, and expected response.\n"
                + "2. Queue execution locally with priority and optional schedule delay.\n"
                + "3. Execute HTTP/API or webhook work from the Android device.\n"
                + "4. Retry transient failures automatically and keep the full result in local history.\n"
                + "5. Export the report from the app for review or handoff.";
    }

    private boolean allowsBody(String method) {
        return "POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method) || "DELETE".equals(method);
    }

    private String readAll(InputStream stream) throws Exception {
        if (stream == null) return "";
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) builder.append(line).append('\n');
        }
        return builder.toString().trim();
    }

    private void updateTask(String taskId, String status, String result, String error, long scheduledAt) {
        synchronized (lock) {
            for (int i = 0; i < tasks.size(); i++) {
                AutomationModels.AutomationTask task = tasks.get(i);
                if (task.id.equals(taskId)) {
                    tasks.set(i, task.withStatus(status, trim(result), error, task.attempts, scheduledAt));
                    persistLocked();
                    return;
                }
            }
        }
    }

    private AutomationModels.AutomationTask findTask(String taskId) {
        synchronized (lock) {
            for (AutomationModels.AutomationTask task : tasks) if (task.id.equals(taskId)) return task;
        }
        return null;
    }

    private int nextReadyIndexLocked(long now) {
        int bestIndex = -1;
        for (int i = 0; i < tasks.size(); i++) {
            AutomationModels.AutomationTask task = tasks.get(i);
            if (!task.isReady(now)) continue;
            if (bestIndex < 0 || task.priority > tasks.get(bestIndex).priority ||
                    (task.priority == tasks.get(bestIndex).priority && task.createdAt < tasks.get(bestIndex).createdAt)) {
                bestIndex = i;
            }
        }
        return bestIndex;
    }

    private void scheduleNextPumpLocked() {
        long now = System.currentTimeMillis();
        long next = Long.MAX_VALUE;
        for (AutomationModels.AutomationTask task : tasks) {
            if (task.isScheduled(now)) next = Math.min(next, task.scheduledAt);
        }
        if (next != Long.MAX_VALUE) {
            mainHandler.postDelayed(this::pump, Math.max(1000L, next - now));
        }
    }

    private void resetRunningTasks() {
        long now = System.currentTimeMillis();
        synchronized (lock) {
            for (int i = 0; i < tasks.size(); i++) {
                AutomationModels.AutomationTask task = tasks.get(i);
                if (AutomationModels.STATUS_RUNNING.equals(task.status)) {
                    tasks.set(i, task.withStatus(AutomationModels.STATUS_QUEUED, task.result, "Recovered after app restart", task.attempts, now));
                }
            }
            persistLocked();
        }
    }

    private void markWorkerDone() {
        synchronized (lock) {
            workerActive = false;
        }
    }

    private long retryDelayMillis(int attempts) {
        return Math.min(60000L, Math.max(1, attempts) * 5000L);
    }

    private void persistLocked() {
        preferences.edit().putString(KEY_TASKS, AutomationModels.toJsonArray(tasks).toString()).apply();
    }

    private AutomationModels.TaskStats statsLocked(long now) {
        int queued = 0;
        int scheduled = 0;
        int running = 0;
        int done = 0;
        int failed = 0;
        for (AutomationModels.AutomationTask task : tasks) {
            if (task.isScheduled(now)) scheduled++;
            else if (AutomationModels.STATUS_QUEUED.equals(task.status)) queued++;
            else if (AutomationModels.STATUS_RUNNING.equals(task.status)) running++;
            else if (AutomationModels.STATUS_DONE.equals(task.status)) done++;
            else if (AutomationModels.STATUS_FAILED.equals(task.status)) failed++;
        }
        return new AutomationModels.TaskStats(queued, scheduled, running, done, failed, paused);
    }

    private void notifyChanged(String event) {
        if (listener == null) return;
        List<AutomationModels.AutomationTask> snapshot;
        AutomationModels.TaskStats stats;
        synchronized (lock) {
            snapshot = new ArrayList<>(tasks);
            stats = statsLocked(System.currentTimeMillis());
        }
        mainHandler.post(() -> listener.onChanged(snapshot, stats, event));
    }

    private String trim(String value) {
        if (value == null) return "";
        return value.length() <= MAX_RESULT_CHARS ? value : value.substring(0, MAX_RESULT_CHARS) + "\n…";
    }
}
