#!/bin/zsh
set -euo pipefail
project_dir="$(cd "$(dirname "$0")/.." && pwd)"
cd "$project_dir"
python3 scripts/check-architecture.py
export JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
sdk_path="${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}"
adb_bin="$sdk_path/platform-tools/adb"
if [[ -z "${ANDROID_SERIAL:-}" ]]; then
  "$adb_bin" devices -l
  print -u2 '请明确指定测试设备，例如 ANDROID_SERIAL=emulator-5556 ./scripts/verify-light-macos.sh'
  exit 2
fi
./gradlew --offline -PisolatedPreview :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug
# Do not clear a user installation if a build was accidentally made without the QA suffix.
python3 - <<'PY'
import json
from pathlib import Path
metadata=json.loads(Path('app/build/outputs/apk/debug/output-metadata.json').read_text())
assert metadata['applicationId']=='com.wardrobe.atelier.light.preview', 'Refusing non-QA APK'
PY
"$adb_bin" -s "$ANDROID_SERIAL" install -r app/build/outputs/apk/debug/app-debug.apk
"$adb_bin" -s "$ANDROID_SERIAL" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
"$adb_bin" -s "$ANDROID_SERIAL" shell pm clear com.wardrobe.atelier.light.preview
report_file="$project_dir/app/build/reports/android-light-tests.txt"
"$adb_bin" -s "$ANDROID_SERIAL" shell am instrument -w com.wardrobe.atelier.light.preview.test/androidx.test.runner.AndroidJUnitRunner | tee "$report_file"
# Android instrumentation can exit 0 even when assertions fail; inspect its result explicitly.
python3 - "$report_file" <<'PY'
import re,sys
from pathlib import Path
text=Path(sys.argv[1]).read_text()
assert re.search(r'OK \(\d+ tests?\)',text), 'Instrumentation did not report success'
assert not any(x in text for x in ['FAILURES!!!','Process crashed','INSTRUMENTATION_FAILED']), text
PY
print '规则测试、lint 和隔离 Android 回归已完成。原版与正式轻量版数据未清除。'
