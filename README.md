# Cortex Native Automation Pro Android

Cortex Native Automation Pro is a standalone Android app for building and running advanced automation tasks directly on a device. It does not require a Python/FastAPI server, remote controller, browser runtime, or `new1.zip` at runtime.

## What the app does

- Runs a persistent on-device queue with pause, resume, retry failed, duplicate latest, clear finished, clear all, and export report controls.
- Executes HTTP/API automation directly from Android with `HttpURLConnection`.
- Supports priorities (`Low`, `Normal`, `High`, `Critical`) so urgent jobs run before normal jobs.
- Supports delayed/scheduled starts for tasks that should not run immediately.
- Supports per-task max retries, automatic retry backoff, and per-task network timeout settings.
- Includes task templates for OpenAI-compatible chat APIs, JSON webhooks, WordPress REST posts, image APIs, and local research planning.
- Lets the user enter headers as either JSON or `Key: Value` lines for bearer tokens, API keys, content types, and webhook metadata.
- Persists the queue locally in Android `SharedPreferences`, so tasks survive app restarts.
- Generates a local automation plan when the task has no target URL, keeping planning available without any external service.
- Exports a plain-text automation report through Android's standard share sheet.

## Security and data handling

This app is intentionally self-contained. It does not copy cookies, `.env` values, databases, task history, or any private server-side artifacts from `new1.zip`. Users enter only the API endpoints and credentials they want to use on the Android device.

## Project layout

- `app/src/main/java/com/cortex/automation/MainActivity.java` — native Android UI, advanced task builder, templates, controls, queue rendering, and report sharing.
- `app/src/main/java/com/cortex/automation/LocalAutomationEngine.java` — local queue manager, persistence, scheduling, priority selection, retries, backoff, report generation, and HTTP executor.
- `app/src/main/java/com/cortex/automation/AutomationModels.java` — task model, stats model, priorities, schedule timestamps, retry configuration, and JSON persistence helpers.
- `app/src/main/AndroidManifest.xml` — launcher activity and network permissions.

## Build

```bash
gradle --no-daemon :app:assembleDebug
```

The project uses the Android Gradle Plugin and Google Maven. Build on a machine with Android SDK/build tooling and repository access.


## Create APK

Build the standalone debug APK from the repository root:

```bash
./scripts/build_apk.sh
```

When Android Gradle Plugin dependencies and the Android SDK are available, the APK is created at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

This repository also includes a GitHub Actions workflow at `.github/workflows/build-apk.yml` that builds the same debug APK on every push, pull request, or manual workflow dispatch and uploads it as an artifact named `cortex-native-automation-pro-debug-apk`. Use that artifact when the local environment cannot reach Google Maven or does not include Android build tooling.
