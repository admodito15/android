package com.cortex.automation;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private LocalAutomationEngine engine;
    private EditText titleInput;
    private EditText urlInput;
    private EditText headersInput;
    private EditText bodyInput;
    private EditText retriesInput;
    private EditText timeoutInput;
    private EditText delayInput;
    private Spinner typeSpinner;
    private Spinner methodSpinner;
    private Spinner prioritySpinner;
    private Spinner templateSpinner;
    private TextView statusText;
    private TextView queueText;
    private TextView logText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        engine = new LocalAutomationEngine(this);
        setContentView(buildLayout());
        engine.setListener(this::renderState);
    }

    private View buildLayout() {
        ScrollView scrollView = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(24), dp(18), dp(24));
        root.setBackgroundColor(Color.rgb(243, 247, 251));
        scrollView.addView(root);

        root.addView(title("Cortex Native Automation Pro"));
        root.addView(body("A complete standalone Android automation console. It owns queueing, scheduling, priorities, retries, local planning, reports, and direct HTTP/API execution on the device — no Python server or remote controller required."));

        LinearLayout statusCard = card();
        statusCard.addView(section("Command center"));
        statusText = body("Starting advanced native automation engine…");
        queueText = body("No tasks yet.");
        statusCard.addView(statusText);
        statusCard.addView(queueText);
        LinearLayout actionRow = row();
        actionRow.addView(button("Pause", v -> engine.pause()));
        actionRow.addView(button("Resume", v -> engine.resume()));
        actionRow.addView(button("Retry Failed", v -> engine.retryFailed()));
        statusCard.addView(actionRow);
        LinearLayout toolsRow = row();
        toolsRow.addView(button("Duplicate Latest", v -> engine.duplicateLatest()));
        toolsRow.addView(button("Export Report", v -> shareReport()));
        statusCard.addView(toolsRow);
        LinearLayout clearRow = row();
        clearRow.addView(button("Clear Finished", v -> engine.clearFinished()));
        clearRow.addView(button("Clear All", v -> engine.clearAll()));
        statusCard.addView(clearRow);
        root.addView(statusCard);

        LinearLayout createCard = card();
        createCard.addView(section("Advanced task builder"));
        createCard.addView(label("Template"));
        templateSpinner = spinner(new String[]{"Custom", "OpenAI-compatible chat", "Webhook JSON", "WordPress REST post", "Image API request", "Local research plan"});
        createCard.addView(templateSpinner);
        createCard.addView(button("Apply Template", v -> applyTemplate()));
        createCard.addView(label("Task title / keyword"));
        titleInput = input("Advanced HTTP automation task");
        createCard.addView(titleInput);
        createCard.addView(label("Automation type"));
        typeSpinner = spinner(new String[]{"HTTP Request", "AI API Workflow", "Webhook", "WordPress REST", "Image API", "Research Plan", "Content Publish"});
        createCard.addView(typeSpinner);
        createCard.addView(label("Method"));
        methodSpinner = spinner(new String[]{"GET", "POST", "PUT", "PATCH", "DELETE"});
        createCard.addView(methodSpinner);
        createCard.addView(label("Priority"));
        prioritySpinner = spinner(new String[]{"Low", "Normal", "High", "Critical"});
        prioritySpinner.setSelection(1);
        createCard.addView(prioritySpinner);
        createCard.addView(label("Target URL (optional for local plan)"));
        urlInput = input("");
        urlInput.setHint("https://api.example.com/endpoint");
        createCard.addView(urlInput);
        createCard.addView(label("Headers (JSON or Key: Value lines)"));
        headersInput = multiInput("Authorization: Bearer token\nContent-Type: application/json", 3);
        createCard.addView(headersInput);
        createCard.addView(label("Body / payload"));
        bodyInput = multiInput("{\n  \"prompt\": \"Create best advanced automation content\"\n}", 6);
        createCard.addView(bodyInput);
        LinearLayout advancedRow = row();
        advancedRow.addView(fieldColumn("Retries", retriesInput = compactInput("2")));
        advancedRow.addView(fieldColumn("Timeout sec", timeoutInput = compactInput("25")));
        advancedRow.addView(fieldColumn("Delay sec", delayInput = compactInput("0")));
        createCard.addView(advancedRow);
        createCard.addView(button("Add Native Automation Task", v -> addTask()));
        root.addView(createCard);

        LinearLayout featureCard = card();
        featureCard.addView(section("Pro capabilities"));
        featureCard.addView(body("• Local persisted queue with pause/resume/retry/duplicate/export/clear controls\n• Priority execution and scheduled delayed starts\n• Automatic retry backoff with per-task max retry limits\n• Per-task network timeout control and URL validation\n• Direct HTTP/API execution from Android using HttpURLConnection\n• Header editor for API keys, bearer tokens, JSON headers, and webhooks\n• Built-in templates for AI APIs, webhooks, WordPress REST, image APIs, and local planning\n• No bundled cookies, no server secrets, and no dependency on new1.zip at runtime"));
        root.addView(featureCard);

        LinearLayout logCard = card();
        logCard.addView(section("Activity log"));
        logText = body("Ready.");
        logText.setTypeface(Typeface.MONOSPACE);
        logCard.addView(logText);
        root.addView(logCard);
        return scrollView;
    }

    private void addTask() {
        String title = titleInput.getText().toString().trim();
        String url = urlInput.getText().toString().trim();
        if (!url.isEmpty() && !url.startsWith("http://") && !url.startsWith("https://")) {
            appendLog("URL must start with http:// or https://");
            return;
        }
        AutomationModels.AutomationTask task = AutomationModels.AutomationTask.create(
                title,
                selected(typeSpinner),
                selected(methodSpinner),
                url,
                headersInput.getText().toString(),
                bodyInput.getText().toString(),
                parseInt(retriesInput, 2),
                parseInt(timeoutInput, 25),
                prioritySpinner.getSelectedItemPosition(),
                parseInt(delayInput, 0)
        );
        engine.addTask(task);
        titleInput.setText("");
        urlInput.setText("");
    }

    private void applyTemplate() {
        String template = selected(templateSpinner);
        if ("OpenAI-compatible chat".equals(template)) {
            titleInput.setText("Generate article with AI API");
            typeSpinner.setSelection(1);
            methodSpinner.setSelection(1);
            prioritySpinner.setSelection(2);
            urlInput.setText("https://api.example.com/v1/chat/completions");
            headersInput.setText("Authorization: Bearer YOUR_API_KEY\nContent-Type: application/json");
            bodyInput.setText("{\n  \"model\": \"model-name\",\n  \"messages\": [{\"role\": \"user\", \"content\": \"Create advanced automation content\"}]\n}");
        } else if ("Webhook JSON".equals(template)) {
            titleInput.setText("Send webhook event");
            typeSpinner.setSelection(2);
            methodSpinner.setSelection(1);
            prioritySpinner.setSelection(1);
            urlInput.setText("https://example.com/webhook");
            headersInput.setText("Content-Type: application/json\nX-Automation-Source: cortex-android");
            bodyInput.setText("{\n  \"event\": \"automation.started\",\n  \"source\": \"android\"\n}");
        } else if ("WordPress REST post".equals(template)) {
            titleInput.setText("Publish WordPress draft");
            typeSpinner.setSelection(3);
            methodSpinner.setSelection(1);
            prioritySpinner.setSelection(2);
            urlInput.setText("https://example.com/wp-json/wp/v2/posts");
            headersInput.setText("Authorization: Bearer YOUR_WORDPRESS_TOKEN\nContent-Type: application/json");
            bodyInput.setText("{\n  \"title\": \"Automation Article\",\n  \"content\": \"Generated from Cortex Native Automation\",\n  \"status\": \"draft\"\n}");
        } else if ("Image API request".equals(template)) {
            titleInput.setText("Generate image through API");
            typeSpinner.setSelection(4);
            methodSpinner.setSelection(1);
            prioritySpinner.setSelection(1);
            urlInput.setText("https://api.example.com/v1/images");
            headersInput.setText("Authorization: Bearer YOUR_API_KEY\nContent-Type: application/json");
            bodyInput.setText("{\n  \"prompt\": \"advanced android automation dashboard, clean UI\",\n  \"size\": \"1024x1024\"\n}");
        } else if ("Local research plan".equals(template)) {
            titleInput.setText("Create local research workflow");
            typeSpinner.setSelection(5);
            methodSpinner.setSelection(0);
            prioritySpinner.setSelection(1);
            urlInput.setText("");
            headersInput.setText("");
            bodyInput.setText("Topic: complete advanced Android automation app\nOutput: checklist, API plan, and publishing steps");
        }
        appendLog("Applied template: " + template);
    }

    private void shareReport() {
        String report = engine.exportReport();
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT, "Cortex Native Automation Report");
        intent.putExtra(Intent.EXTRA_TEXT, report);
        startActivity(Intent.createChooser(intent, "Export automation report"));
    }

    private void renderState(List<AutomationModels.AutomationTask> tasks, AutomationModels.TaskStats stats, String event) {
        statusText.setText(stats.summary());
        queueText.setText(formatTasks(tasks));
        appendLog(event);
    }

    private String formatTasks(List<AutomationModels.AutomationTask> tasks) {
        if (tasks.isEmpty()) return "No tasks yet. Add an HTTP/API workflow or create a local plan.";
        StringBuilder builder = new StringBuilder();
        long now = System.currentTimeMillis();
        int count = Math.min(tasks.size(), 15);
        for (int i = 0; i < count; i++) {
            AutomationModels.AutomationTask task = tasks.get(i);
            builder.append("#").append(i + 1).append(" ")
                    .append(task.status.toUpperCase(Locale.US)).append(" • ")
                    .append(task.priorityLabel()).append(" • ")
                    .append(task.title).append("\n")
                    .append(task.type).append(" • ").append(task.method);
            if (!task.url.isEmpty()) builder.append(" • ").append(task.url);
            builder.append("\nAttempts: ").append(task.attempts).append("/").append(task.maxRetries)
                    .append(" • Timeout: ").append(task.timeoutSeconds).append("s")
                    .append(" • Updated: ").append(task.displayUpdatedTime()).append("\n");
            if (task.isScheduled(now)) builder.append("Scheduled: ").append(task.displayTime(task.scheduledAt)).append("\n");
            if (!task.error.isEmpty()) builder.append("Error: ").append(task.error).append("\n");
            if (!task.result.isEmpty()) builder.append(task.result).append("\n");
            builder.append("\n");
        }
        if (tasks.size() > count) builder.append("+").append(tasks.size() - count).append(" more tasks");
        return builder.toString().trim();
    }

    private void appendLog(String message) {
        if (logText == null || message == null || message.isEmpty()) return;
        String stamp = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
        String current = logText.getText().toString();
        String line = stamp + "  " + message;
        logText.setText("Ready.".equals(current) ? line : line + "\n" + current);
    }

    private int parseInt(EditText editText, int fallback) {
        try {
            return Integer.parseInt(editText.getText().toString().trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private String selected(Spinner spinner) {
        Object item = spinner.getSelectedItem();
        return item == null ? "" : item.toString();
    }

    private TextView title(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(28);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setTextColor(Color.rgb(16, 24, 39));
        view.setPadding(0, 0, 0, dp(8));
        return view;
    }

    private TextView section(String text) {
        TextView view = label(text);
        view.setTextSize(20);
        view.setTextColor(Color.rgb(17, 94, 89));
        return view;
    }

    private TextView label(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(14);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setTextColor(Color.rgb(55, 65, 81));
        view.setPadding(0, dp(10), 0, dp(6));
        return view;
    }

    private TextView body(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(15);
        view.setTextColor(Color.rgb(55, 65, 81));
        view.setLineSpacing(0, 1.15f);
        view.setPadding(0, dp(4), 0, dp(6));
        return view;
    }

    private EditText input(String text) {
        EditText editText = new EditText(this);
        editText.setText(text);
        editText.setSingleLine(true);
        editText.setTextSize(15);
        editText.setPadding(dp(12), dp(8), dp(12), dp(8));
        editText.setBackgroundColor(Color.WHITE);
        return editText;
    }

    private EditText compactInput(String text) {
        EditText editText = input(text);
        editText.setGravity(Gravity.CENTER);
        return editText;
    }

    private LinearLayout fieldColumn(String label, EditText editText) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(0, 0, dp(8), 0);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        layout.setLayoutParams(params);
        layout.addView(label(label));
        layout.addView(editText);
        return layout;
    }

    private EditText multiInput(String text, int lines) {
        EditText editText = input(text);
        editText.setSingleLine(false);
        editText.setMinLines(lines);
        editText.setGravity(Gravity.TOP | Gravity.START);
        editText.setTypeface(Typeface.MONOSPACE);
        return editText;
    }

    private Spinner spinner(String[] values) {
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, values);
        spinner.setAdapter(adapter);
        spinner.setPadding(0, dp(4), 0, dp(4));
        return spinner;
    }

    private Button button(String text, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setOnClickListener(listener);
        return button;
    }

    private LinearLayout row() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        layout.setPadding(0, dp(8), 0, 0);
        return layout;
    }

    private LinearLayout card() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(14), dp(14), dp(14), dp(14));
        layout.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, dp(12), 0, dp(8));
        layout.setLayoutParams(params);
        return layout;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
