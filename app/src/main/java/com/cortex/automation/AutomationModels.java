package com.cortex.automation;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

final class AutomationModels {
    static final String STATUS_QUEUED = "queued";
    static final String STATUS_RUNNING = "running";
    static final String STATUS_DONE = "done";
    static final String STATUS_FAILED = "failed";

    private AutomationModels() {}

    static final class TaskStats {
        final int queued;
        final int scheduled;
        final int running;
        final int done;
        final int failed;
        final boolean paused;

        TaskStats(int queued, int scheduled, int running, int done, int failed, boolean paused) {
            this.queued = queued;
            this.scheduled = scheduled;
            this.running = running;
            this.done = done;
            this.failed = failed;
            this.paused = paused;
        }

        String summary() {
            String state = paused ? "Paused" : running > 0 ? "Running" : queued > 0 ? "Ready" : scheduled > 0 ? "Scheduled" : "Idle";
            return state + " • queued " + queued + " • scheduled " + scheduled + " • running " + running
                    + " • done " + done + " • failed " + failed;
        }
    }

    static final class AutomationTask {
        final String id;
        final String title;
        final String type;
        final String method;
        final String url;
        final String headers;
        final String body;
        final String status;
        final String result;
        final String error;
        final int attempts;
        final int maxRetries;
        final int timeoutSeconds;
        final int priority;
        final long scheduledAt;
        final long createdAt;
        final long updatedAt;

        AutomationTask(String id, String title, String type, String method, String url, String headers, String body,
                       String status, String result, String error, int attempts, int maxRetries, int timeoutSeconds,
                       int priority, long scheduledAt, long createdAt, long updatedAt) {
            this.id = id;
            this.title = title;
            this.type = type;
            this.method = method;
            this.url = url;
            this.headers = headers;
            this.body = body;
            this.status = status;
            this.result = result;
            this.error = error;
            this.attempts = attempts;
            this.maxRetries = maxRetries;
            this.timeoutSeconds = timeoutSeconds;
            this.priority = priority;
            this.scheduledAt = scheduledAt;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
        }

        static AutomationTask create(String title, String type, String method, String url, String headers, String body,
                                     int maxRetries, int timeoutSeconds, int priority, int delaySeconds) {
            long now = System.currentTimeMillis();
            return new AutomationTask(
                    "task-" + now,
                    emptyToDefault(title, "Untitled automation"),
                    emptyToDefault(type, "HTTP Request"),
                    emptyToDefault(method, "GET"),
                    safe(url),
                    safe(headers),
                    safe(body),
                    STATUS_QUEUED,
                    "",
                    "",
                    0,
                    clamp(maxRetries, 0, 10),
                    clamp(timeoutSeconds, 5, 120),
                    clamp(priority, 0, 3),
                    now + (Math.max(0, delaySeconds) * 1000L),
                    now,
                    now
            );
        }

        AutomationTask withStatus(String nextStatus, String nextResult, String nextError, int nextAttempts, long nextScheduledAt) {
            return new AutomationTask(id, title, type, method, url, headers, body, nextStatus,
                    nextResult == null ? result : nextResult,
                    nextError == null ? error : nextError,
                    nextAttempts,
                    maxRetries,
                    timeoutSeconds,
                    priority,
                    nextScheduledAt,
                    createdAt,
                    System.currentTimeMillis());
        }

        AutomationTask duplicate() {
            long now = System.currentTimeMillis();
            return new AutomationTask(
                    "task-" + now,
                    title + " copy",
                    type,
                    method,
                    url,
                    headers,
                    body,
                    STATUS_QUEUED,
                    "",
                    "",
                    0,
                    maxRetries,
                    timeoutSeconds,
                    priority,
                    now,
                    now,
                    now
            );
        }

        JSONObject toJson() {
            JSONObject json = new JSONObject();
            try {
                json.put("id", id);
                json.put("title", title);
                json.put("type", type);
                json.put("method", method);
                json.put("url", url);
                json.put("headers", headers);
                json.put("body", body);
                json.put("status", status);
                json.put("result", result);
                json.put("error", error);
                json.put("attempts", attempts);
                json.put("maxRetries", maxRetries);
                json.put("timeoutSeconds", timeoutSeconds);
                json.put("priority", priority);
                json.put("scheduledAt", scheduledAt);
                json.put("createdAt", createdAt);
                json.put("updatedAt", updatedAt);
            } catch (Exception ignored) {
                // JSONObject only throws for unsupported values; all fields are primitives/strings.
            }
            return json;
        }

        static AutomationTask fromJson(JSONObject json) {
            long now = System.currentTimeMillis();
            return new AutomationTask(
                    json.optString("id", "task-" + now),
                    json.optString("title", "Untitled automation"),
                    json.optString("type", "HTTP Request"),
                    json.optString("method", "GET"),
                    json.optString("url", ""),
                    json.optString("headers", ""),
                    json.optString("body", ""),
                    json.optString("status", STATUS_QUEUED),
                    json.optString("result", ""),
                    json.optString("error", ""),
                    json.optInt("attempts", 0),
                    json.optInt("maxRetries", 2),
                    json.optInt("timeoutSeconds", 25),
                    json.optInt("priority", 1),
                    json.optLong("scheduledAt", now),
                    json.optLong("createdAt", now),
                    json.optLong("updatedAt", now)
            );
        }

        boolean isReady(long now) {
            return STATUS_QUEUED.equals(status) && scheduledAt <= now;
        }

        boolean isScheduled(long now) {
            return STATUS_QUEUED.equals(status) && scheduledAt > now;
        }

        String priorityLabel() {
            if (priority >= 3) return "Critical";
            if (priority == 2) return "High";
            if (priority == 1) return "Normal";
            return "Low";
        }

        String displayTime(long value) {
            return new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date(value));
        }

        String displayUpdatedTime() {
            return displayTime(updatedAt);
        }

        private static String emptyToDefault(String value, String fallback) {
            String cleaned = safe(value);
            return cleaned.isEmpty() ? fallback : cleaned;
        }

        private static String safe(String value) {
            return value == null ? "" : value.trim();
        }
    }

    static JSONArray toJsonArray(List<AutomationTask> tasks) {
        JSONArray array = new JSONArray();
        for (AutomationTask task : tasks) array.put(task.toJson());
        return array;
    }

    static List<AutomationTask> fromJsonArray(String raw) {
        List<AutomationTask> tasks = new ArrayList<>();
        if (raw == null || raw.trim().isEmpty()) return tasks;
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject json = array.optJSONObject(i);
                if (json != null) tasks.add(AutomationTask.fromJson(json));
            }
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
        return tasks;
    }

    static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
