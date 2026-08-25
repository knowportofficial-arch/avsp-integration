#!/usr/bin/env bash
set -euo pipefail

export ANDROID_HOME="${ANDROID_HOME:-/opt/android-sdk}"
export PATH="${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools:${PATH}"

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
for project in CURRENT_M1_M3 M6/android M7/android; do
  if [ -f "$ROOT/$project/gradlew" ]; then
    chmod +x "$ROOT/$project/gradlew"
  fi
done

ffmpeg -version >/dev/null
ffprobe -version >/dev/null
tesseract --version >/dev/null
java -version >/dev/null 2>&1
python3 --version >/dev/null
test -d "$ANDROID_HOME/platform-tools"

echo "AVSP environment ready."
