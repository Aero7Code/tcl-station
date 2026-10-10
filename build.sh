#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Work/tools/android-sdk}}"
TOOLS="$SDK/build-tools/36.0.0"
ANDROID_JAR="$SDK/platforms/android-36/android.jar"
for f in "$TOOLS/aapt2" "$TOOLS/d8" "$TOOLS/zipalign" "$TOOLS/apksigner" "$ANDROID_JAR"; do test -e "$f" || { printf 'Missing tool: %s\n' "$f" >&2; exit 1; }; done
export JAVA_HOME="$(mise where java@21.0.2)"
export PATH="$JAVA_HOME/bin:$PATH"
# Downloads are cached, but checksum-checked on every build including cache reuse.
mkdir -p build/offline-cache
fetch_verified() {
  local url="$1" digest="$2" output="$3"
  if [[ -f "$output" ]] && printf '%s  %s\n' "$digest" "$output" | sha256sum -c --status; then return; fi
  rm -f "$output"
  curl -fL --retry 3 -o "$output.tmp" "$url"
  if ! printf '%s  %s\n' "$digest" "$output.tmp" | sha256sum -c --status; then
    rm -f "$output.tmp"
    printf 'SHA-256 mismatch: %s\n' "$url" >&2
    exit 1
  fi
  mv "$output.tmp" "$output"
}
fetch_verified 'https://repo.maven.apache.org/maven2/com/alphacephei/vosk-android/0.3.75/vosk-android-0.3.75.aar' \
  ab2f8b91ac8051561aa325546b35fed9a68b36b8121bac5c6fb927525c4adfad build/offline-cache/vosk-android-0.3.75.aar
fetch_verified 'https://repo.maven.apache.org/maven2/net/java/dev/jna/jna/5.18.1/jna-5.18.1.aar' \
  7f053e3ec99e14dd71259c82c1c8a02738d64a13c31226b2acc170f3060951e0 build/offline-cache/jna-5.18.1.aar
fetch_verified 'https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip' \
  30f26242c4eb449f948e42cb302dd7a686cb29a3423a8367f99ff41780942498 build/offline-cache/vosk-model-small-en-us-0.15.zip

# Drop stale classes, assets and libraries before recreating the APK payload.
rm -rf build/test-classes build/classes build/dex build/offline-package build/offline-jars
mkdir -p build/test-classes build/classes build/dex build/offline-package/lib/arm64-v8a build/offline-package/assets/model-en-us build/offline-jars
python3 - <<'PY'
from pathlib import Path
from zipfile import ZipFile
from shutil import copyfileobj, copyfile

cache = Path('build/offline-cache')
root = Path('build/offline-package')
for artifact, native in [('vosk-android-0.3.75.aar', 'libvosk.so'),
                         ('jna-5.18.1.aar', 'libjnidispatch.so')]:
    with ZipFile(cache / artifact) as aar:
        for src, dest in [('classes.jar', Path('build/offline-jars') / artifact.replace('.aar', '.jar')),
                          ('jni/arm64-v8a/' + native, root / 'lib/arm64-v8a' / native)]:
            with aar.open(src) as infile, dest.open('wb') as outfile:
                copyfileobj(infile, outfile)

prefix = 'vosk-model-small-en-us-0.15/'
with ZipFile(cache / 'vosk-model-small-en-us-0.15.zip') as model:
    for item in model.infolist():
        name = item.filename
        assert name.startswith(prefix), f'Unexpected model path: {name}'
        relative = name[len(prefix):]
        if not relative or item.is_dir():
            continue
        parts = Path(relative).parts
        assert all(part not in ('', '.', '..') for part in parts) and not relative.startswith('/'), name
        assert ((item.external_attr >> 16) & 0o170000) != 0o120000, f'Model symlink: {name}'
        dest = root / 'assets/model-en-us' / relative
        dest.parent.mkdir(parents=True, exist_ok=True)
        with model.open(item) as infile, dest.open('wb') as outfile:
            copyfileobj(infile, outfile)
# Vosk's Android asset unpacker uses uuid as a deterministic copy/version marker.
(root / 'assets/model-en-us/uuid').write_text('30f26242c4eb449f948e42cb302dd7a686cb29a3423a8367f99ff41780942498\n')
copyfile('THIRD_PARTY_NOTICES.md', root / 'assets/THIRD_PARTY_NOTICES.md')
PY
python3 scripts/generate_config.py
python3 tests/home_manifest_test.py
python3 tests/listener_manifest_test.py
python3 tests/listener_tts_safety_test.py
python3 tests/voice_choice_source_test.py
python3 tests/tap_to_talk_source_test.py
python3 tests/voice_action_source_test.py
python3 tests/weather_feature_source_test.py
python3 tests/diagnostic_event_source_test.py
python3 tests/diagnostic_collector_test.py
python3 tests/tts_bind_timeout_source_test.py
mkdir -p build/test-classes
javac -d build/test-classes src/com/aero/tclstation/StationDiagnosticLog.java tests/StationDiagnosticLogTest.java
java -cp build/test-classes com.aero.tclstation.StationDiagnosticLogTest
javac -d build/test-classes src/com/aero/tclstation/WeatherModel.java src/com/aero/tclstation/WeatherForecast.java src/com/aero/tclstation/WeatherFreshness.java src/com/aero/tclstation/PanelState.java src/com/aero/tclstation/LegacyClipCleanup.java src/com/aero/tclstation/CameraOpenGate.java src/com/aero/tclstation/CameraCloseHandoff.java src/com/aero/tclstation/VoiceCommand.java src/com/aero/tclstation/HandsFreeGate.java tests/WeatherModelTest.java tests/WeatherForecastTest.java tests/WeatherFreshnessTest.java tests/PanelStateTest.java tests/LegacyClipCleanupTest.java tests/CameraOpenGateTest.java tests/CameraCloseHandoffTest.java tests/VoiceCommandTest.java tests/HandsFreeGateTest.java
javac -d build/test-classes tests/TtsMicHoldTest.java
java -cp build/test-classes com.aero.tclstation.WeatherModelTest
java -cp build/test-classes com.aero.tclstation.WeatherForecastTest
java -cp build/test-classes com.aero.tclstation.WeatherFreshnessTest
java -cp build/test-classes com.aero.tclstation.PanelStateTest
java -cp build/test-classes com.aero.tclstation.LegacyClipCleanupTest
java -cp build/test-classes com.aero.tclstation.CameraOpenGateTest
java -cp build/test-classes com.aero.tclstation.CameraCloseHandoffTest
java -cp build/test-classes com.aero.tclstation.VoiceCommandTest
java -cp build/test-classes com.aero.tclstation.HandsFreeGateTest
java -cp build/test-classes com.aero.tclstation.TtsMicHoldTest
javac -source 8 -target 8 -Xlint:-options -cp "$ANDROID_JAR:build/offline-jars/vosk-android-0.3.75.jar:build/offline-jars/jna-5.18.1.jar" -d build/classes src/com/aero/tclstation/*.java build/generated/StationConfig.java
"$TOOLS/d8" --min-api 31 --lib "$ANDROID_JAR" --output build/dex build/classes/com/aero/tclstation/*.class build/offline-jars/*.jar
"$TOOLS/aapt2" compile --dir res -o build/res.zip
"$TOOLS/aapt2" link -I "$ANDROID_JAR" --manifest AndroidManifest.xml --auto-add-overlay -R build/res.zip -A build/offline-package/assets -o build/unsigned.apk
(cd build/dex && zip -q -0 ../unsigned.apk classes*.dex)
(cd build/offline-package && zip -q -0 ../unsigned.apk lib/arm64-v8a/*.so)
"$TOOLS/zipalign" -f 4 build/unsigned.apk build/aligned.apk
if [[ ! -f build/debug.keystore ]]; then
  keytool -genkeypair -keystore build/debug.keystore -storepass android -keypass android -alias tclstation -keyalg RSA -keysize 2048 -validity 3650 -dname 'CN=TCL Station Prototype' -noprompt >/dev/null 2>&1
fi
"$TOOLS/apksigner" sign --ks build/debug.keystore --ks-key-alias tclstation --ks-pass pass:android --key-pass pass:android --out build/tcl-station-debug.apk build/aligned.apk
"$TOOLS/apksigner" verify --verbose build/tcl-station-debug.apk
"$TOOLS/aapt" dump badging build/tcl-station-debug.apk | /usr/bin/grep -E '^(package:|sdkVersion:|targetSdkVersion:|launchable-activity:)'
python3 tests/no_recording_artifacts.py
python3 tests/apk_offline_model.py
printf 'APK: %s/build/tcl-station-debug.apk\n' "$PWD"
