#!/usr/bin/env bash
set -euo pipefail

export ANDROID_HOME="${ANDROID_HOME:-/opt/android-sdk}"
export PATH="${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools:${PATH}"

ffmpeg -version >/dev/null
ffprobe -version >/dev/null
tesseract --version >/dev/null
java -version >/dev/null 2>&1
python3 --version >/dev/null
test -d "$ANDROID_HOME/platform-tools"

echo "AVSP environment ready."
