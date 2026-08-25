#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
export ANDROID_HOME="${ANDROID_HOME:-/opt/android-sdk}"

setup_python_module() {
  local module_dir="$1"
  cd "$ROOT/$module_dir"
  if [ ! -f .venv/bin/activate ]; then
    rm -rf .venv
    python3 -m venv .venv
  fi
  # shellcheck disable=SC1091
  source .venv/bin/activate
  pip install -U pip --quiet
  pip install -r requirements.txt --quiet
  deactivate
}

for module in \
  "M4/AVSP_M4_Video_Engine" \
  "M5/m5_youtube_screen_input" \
  "M8/m8" \
  "M9/m9"
do
  setup_python_module "$module"
done

cd "$ROOT/M5/m5_youtube_screen_input"
# shellcheck disable=SC1091
source .venv/bin/activate
python test_media/generate_test_media.py
deactivate

for project in CURRENT_M1_M3 M6/android M7/android; do
  printf 'sdk.dir=%s\n' "$ANDROID_HOME" > "$ROOT/$project/local.properties"
  chmod +x "$ROOT/$project/gradlew"
done

cd "$ROOT/CURRENT_M1_M3" && ./gradlew --version >/dev/null
cd "$ROOT/M6/android" && ./gradlew --version >/dev/null
cd "$ROOT/M7/android" && ./gradlew --version >/dev/null

echo "AVSP install complete."
