# TCL Station

An **unofficial, early-stage** Android 12+ dashboard for repurposing a tablet as a tabletop control panel. The app is written in Java with the Android SDK and does not require Google Play services. It is not affiliated with TCL, Amazon, or Google.

## What works now

- Landscape-first, three-page dashboard with seven tap-to-expand cards; double-tap a card to collapse it.
- Weather from Open-Meteo for a **configurable fixed area** (default: example Denver city-center point; no GPS permission), Denver-time clock, and Android camera/timer/alarm and YouTube Music shortcuts.
- A short, intentionally quiet audio-test beep. Android manages wired output; Echo Studio aux playback was tested with one tablet, but this app does **not** control Echo firmware or the speaker's microphone.
- **Experimental** explicit local video-only recording with visible foreground notification and Stop action. Segments rotate roughly every five minutes; completed clips are capped at 20 and a bounded storage budget. A start/stop device trial ran, but playable-clip retrieval, unattended duration, and recovery still need verification. Do not rely on it for security monitoring.

**Not implemented:** Hermes connection, remote camera control, voice wake-up/commands, motion alerts, and EQ. Manual photos/videos are handled by other camera apps. The app has no accounts, analytics, or media upload; see [Privacy Policy](PRIVACY.md).

## Build

Install Android SDK **platform android-36** and **build-tools 36.0.0**, `mise` with `java@21.0.2`, Python 3, and standard JDK tools. Set `ANDROID_HOME` or `ANDROID_SDK_ROOT` to the Android SDK path; otherwise the script checks `$HOME/Work/tools/android-sdk`. Run:

```bash
bash build.sh
```

This runs 10 weather, 11 panel, and 8 recording-policy JVM checks, compiles the app, and produces `build/tcl-station-debug.apk`, signed by a locally generated **debug/prototype key**. Do not commit or distribute the private key. The APK is not a production-signed release. To install on your own authorized tablet, use `adb install -r build/tcl-station-debug.apk` and grant camera permission only when you intend to record.

### Private weather override

The repository uses a generic city-center example. To choose your own area, copy `station.local.properties.example` to `station.local.properties` and edit `name`, `latitude`, and `longitude`. The private file, generated `StationConfig.java`, APK, keystore, and local recordings are excluded from Git. **Do not post your local config or APK in public issues if it reveals your location.** This prototype supports the `America/Denver` timezone only.

Weather data is provided by [Open-Meteo](https://open-meteo.com/), subject to its [terms and attribution/non-commercial-use conditions](https://open-meteo.com/en/terms). Review them before commercial distribution. For how the app handles camera and weather data, read [Privacy Policy](PRIVACY.md) and [Terms of Service / Use](TERMS_OF_SERVICE.md). Source code is [MIT licensed](LICENSE).
