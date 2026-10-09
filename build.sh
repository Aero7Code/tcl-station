#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Work/tools/android-sdk}}"
TOOLS="$SDK/build-tools/36.0.0"
ANDROID_JAR="$SDK/platforms/android-36/android.jar"
for f in "$TOOLS/aapt2" "$TOOLS/d8" "$TOOLS/zipalign" "$TOOLS/apksigner" "$ANDROID_JAR"; do test -e "$f" || { printf 'Missing tool: %s\n' "$f" >&2; exit 1; }; done
export JAVA_HOME="$(mise where java@21.0.2)"
export PATH="$JAVA_HOME/bin:$PATH"
mkdir -p build/test-classes build/classes build/dex
python3 scripts/generate_config.py
javac -d build/test-classes src/com/aero/tclstation/WeatherModel.java src/com/aero/tclstation/PanelState.java src/com/aero/tclstation/RecordingPolicy.java tests/WeatherModelTest.java tests/PanelStateTest.java tests/RecordingPolicyTest.java
java -cp build/test-classes com.aero.tclstation.WeatherModelTest
java -cp build/test-classes com.aero.tclstation.PanelStateTest
java -cp build/test-classes com.aero.tclstation.RecordingPolicyTest
javac -source 8 -target 8 -Xlint:-options -cp "$ANDROID_JAR" -d build/classes src/com/aero/tclstation/*.java build/generated/StationConfig.java
"$TOOLS/d8" --min-api 31 --lib "$ANDROID_JAR" --output build/dex build/classes/com/aero/tclstation/*.class
"$TOOLS/aapt2" compile --dir res -o build/res.zip
"$TOOLS/aapt2" link -I "$ANDROID_JAR" --manifest AndroidManifest.xml --auto-add-overlay -R build/res.zip -o build/unsigned.apk
(cd build/dex && zip -q -0 ../unsigned.apk classes.dex)
"$TOOLS/zipalign" -f 4 build/unsigned.apk build/aligned.apk
if [[ ! -f build/debug.keystore ]]; then
  keytool -genkeypair -keystore build/debug.keystore -storepass android -keypass android -alias tclstation -keyalg RSA -keysize 2048 -validity 3650 -dname 'CN=TCL Station Prototype' -noprompt >/dev/null 2>&1
fi
"$TOOLS/apksigner" sign --ks build/debug.keystore --ks-key-alias tclstation --ks-pass pass:android --key-pass pass:android --out build/tcl-station-debug.apk build/aligned.apk
"$TOOLS/apksigner" verify --verbose build/tcl-station-debug.apk
"$TOOLS/aapt" dump badging build/tcl-station-debug.apk | /usr/bin/grep -E '^(package:|sdkVersion:|targetSdkVersion:|launchable-activity:)'
printf 'APK: %s/build/tcl-station-debug.apk\n' "$PWD"
