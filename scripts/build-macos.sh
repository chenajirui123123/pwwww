#!/bin/zsh
set -euo pipefail
project_dir="$(cd "$(dirname "$0")/.." && pwd)"
cd "$project_dir"
python3 scripts/check-architecture.py
if [[ -d '/Applications/Android Studio.app/Contents/jbr/Contents/Home' ]]; then
  export JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home'
elif [[ -z "${JAVA_HOME:-}" ]]; then
  print -u2 '请先在 Android Studio 配置 Gradle JDK，或设置 JAVA_HOME 为 JDK 21。'
  exit 1
fi
if [[ ! -f local.properties ]]; then
  sdk_path="${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}"
  if [[ ! -d "$sdk_path/platforms/android-36" ]]; then
    print -u2 '请通过 Android Studio SDK Manager 安装 Android API 36，并配置 SDK 路径。'
    exit 1
  fi
  printf 'sdk.dir=%s\n' "$sdk_path" > local.properties
fi
./gradlew :app:assembleDebug :app:testDebugUnitTest "$@"
print '构建完成：app/build/outputs/apk/debug/app-debug.apk'
