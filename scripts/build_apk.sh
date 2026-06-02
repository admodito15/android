#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

GRADLE_CMD="${GRADLE_CMD:-gradle}"
if [[ -x "$ROOT_DIR/gradlew" ]]; then
  GRADLE_CMD="$ROOT_DIR/gradlew"
fi

if ! command -v "${GRADLE_CMD%% *}" >/dev/null 2>&1 && [[ ! -x "$GRADLE_CMD" ]]; then
  echo "Gradle is required to build the APK. Install Gradle or add a Gradle wrapper." >&2
  exit 1
fi

echo "Building Cortex Native Automation Pro debug APK..."
"$GRADLE_CMD" --no-daemon :app:assembleDebug

APK_PATH="$ROOT_DIR/app/build/outputs/apk/debug/app-debug.apk"
if [[ ! -f "$APK_PATH" ]]; then
  echo "Build finished but APK was not found at $APK_PATH" >&2
  exit 1
fi

BYTES=$(wc -c < "$APK_PATH")
echo "APK created: $APK_PATH ($BYTES bytes)"
