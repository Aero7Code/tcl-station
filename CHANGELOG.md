# Changelog

## [0.1.0-alpha.6] — 2026-10-09 (unreleased development source; not a downloadable release)

- Add a bounded on-device diagnostic event log (two rotating 32 KiB files) for speech binding, enqueue, timeout, lifecycle, and safety exits. An ADB-only collector captures these events, a narrowly filtered Android TTS snapshot, and Station process exit-info into private local snapshots. No speech content or raw audio is logged.
- Reproduced the reported “Speech stalled” exit on the target tablet: the separate offline-model TTS engine never completed initialization, Android reported a binding timeout, and Station deliberately killed its process after `stop()` failed on an engine that had never bound. Fix this specific pre-bind case by canceling the pending reply without killing Station; the service still stops hands-free on timeout. Repeated the test on-device: the same bind timeout stopped the listener but Station stayed open. An already queued utterance with unconfirmed stop still fails closed.

- Keep the explicitly enabled hands-free choice in app-private preferences. Request eligible Android sticky-service restarts and resume it when Station becomes visible; explicit Stop and safety failures turn it off. Android can interrupt microphone use; there is no boot receiver or uninterrupted-operation guarantee. On Android 13+ request notification permission before listening, and stop if notifications are blocked so the Stop action remains visible. Extend the wake follow-up from 35 to 45 seconds for multiple different commands; another “Hey Station” resets the window.
- Make recognized weather commands expand the Weather panel; from another app, attempt to open it and offer a notification when Android restricts background launching. Extend short-phrase matching to common forecast/temperature/rain/snow questions, without claiming universal natural-language understanding.
- Let a second press on an expanded Weather card open a full-screen detail view with condition-matched animated sky, current details and a real Open-Meteo seven-day response. The lower scroll region shows the rest of the week. Missing/stale network data is never substituted for live conditions or invented daily values; an open card refreshes at observation/cache expiry and Denver midnight, and old saved weather summaries are removed. Validate the observation timestamp, today's forecast date, daylight flag, and subzero apparent temperature.
- Known prototype limitation: the separately installed model TTS engine intermittently times out when cold on the target tablet. A stalled response fails closed and stops hands-free. Real in-room spoken Weather navigation remains unverified.

- Replace the old 100-synonym/ten-action catalog with 100 documented phrases across 15 bounded actions. Add explicit front/back live preview, Android Clock alarm review at a specified time, and Play/Pause/Next Android media keys (Play is sent even if playback is paused). Commands are parser mappings, not speech-model training or proof of acoustic recognition; no security-camera recording is added.

- Replace the prior same-sounding Android TTS voice selection with a separately installed sherpa-onnx offline model engine for both foreground and hands-free replies. The system engine remains the reversible default; installing the upstream model APK is a separate user action.
- The new engine choice uses a **new opt-in preference**. An alpha.5 alternate-voice selection remains on the system voice after upgrade until the user explicitly selects the separate local model engine; this avoids silently routing speech text to a newly installed app. The model-only path verifies Android's bound engine before passing it any spoken text; if Android falls back to another provider, Station refuses to synthesize. This uses a private engine-identity field on the tested Android 12 build and fails closed if a future Android release blocks that check.
- Test a female US English Kristin model, chosen as a somewhat lower-voiced candidate than Amy using the publishers' sample clips. Request 1.08× model speech speed without changing system voice rate or pitch. The engine does not implement Android pitch changes; perceived warmth/depth require listening on the actual speaker.
- Unify push-to-talk **Speak** with hands-free on the bundled local Vosk model and command grammar. Speak remains an explicit one-shot tap (nine-second limit, tap again to cancel, stops on background); the two recognizers share the on-disk model but have separate in-memory instances. Serialize first extraction to avoid an app-start race and pause/resume hands-free around Speak. No separate Google recognition UI or recognition-provider upload is used by Station's Speak path.
- Fix a failed cold engine initialization leaving Station unable to retry a spoken reply until restart.

## [0.1.0-alpha.5] — 2026-10-09

- Extend the single follow-up after standalone “Hey Station” from 15 to 35 seconds; same-phrase commands and the explicit Stop control are unchanged.
- Add 100 short, locally matched phrase variants for ten existing actions, documented and checked against both wake forms. These do not add 100 distinct capabilities; acoustic success varies by room and pronunciation.
- Add a switch between the tablet's original TTS voice and an alternate local English voice (with fallback to the system voice). The user can compare the samples; no claim of objectively improved sound quality.
- The user reported that hands-free works in the room; not every new phrase or the alternate voice has been acoustically checked.

## [0.1.0-alpha.4] — 2026-10-09

- Accept “Hey Station” as well as “Station” at the beginning of final offline recognized commands. Standalone “Hey Station” triggers “I'm listening” and admits one bounded command in the next 15 seconds. This remains full-phrase recognition, **not** a dedicated hotword detector. Keep Speak push-to-talk independent.
- Add **Test spoken reply** beside the audio beep. Android TTS synthesized the test and started a media audio player routed to the tablet's wired output; microphone capture paused during speech and resumed afterward. Hearing it from the Echo Studio still needs an in-room check.
- Fix the listener staying paused if a canceled external Speak recognizer returns to another launcher instead of resuming Station. The device handoff and screen-off/stop/restart tests passed after the fix.
- A synthetic wake phrase played through the tablet's wired audio output did **not** stop the listener. Acoustic feedback, speaker volume, and recognized words were not established; **human-spoken wake and audible external-speaker replies are not yet verified**.

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
