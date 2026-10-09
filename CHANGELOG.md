# Changelog

## [0.1.0-alpha.3] — 2026-10-09

### Added
- Visible tap-to-talk controls, a bounded set of locally matched speech commands, and spoken weather/time feedback via Android text-to-speech.
- Optional Station-as-Home choice and an in-app shortcut to Android Home settings for reversal.
- Unit tests for command parsing, Home eligibility, and on-device smoke checks for speech handoff and launcher settings.
- Opt-in **Hands-free** foreground microphone listener using a bundled offline Vosk English model. Commands require a recognized “Station” prefix; a notification offers **Stop listening**, and the in-app toggle also stops it. It is not started at boot. Speak push-to-talk remains a separate external-recognizer option.
- Background spoken time and last-cached-weather answers while hands-free is running; other hands-free commands require Station in the foreground. Local weather summary cache in app-private preferences, plus third-party notices for the bundled model and native libraries.

### Changed
- Updated privacy and use documents to disclose continuous tablet-microphone capture while enabled, including screen-off and other-app use, and distinguish offline local recognition from independent Android Speak/TTS providers. Camera remains live-preview-only.

Automated Android 12 device checks confirmed opt-in microphone capture, screen-off continuation, push-to-talk handoff, permission denial, and explicit stop. **Spoken-command recognition and audible replies have not yet been verified**; this is an experimental alpha. Continuous capture can consume battery or misrecognize phrases.

## [0.1.0-alpha.2] — 2026-10-09

### Added
- In-app, foreground-only live camera preview with front/back switching.
- Tests for camera lifecycle, retired-clip cleanup, and recorder-free APK packaging.

### Removed
- Experimental local recording service, controls, permissions, and recording tests.

### Changed
- On upgrade, attempt to remove only clips matching the retired recorder's exact filename pattern in its app-private directory.
- Update the privacy policy, terms, and README for live-only camera behavior.

The previous alpha (`v0.1.0-alpha.1`) included an experimental recorder; its historical source remains in Git history, but that version is not the current build.
