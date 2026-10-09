# Terms of Service / Use — TCL Station

Effective: October 9, 2026

TCL Station is an independent, experimental Android dashboard distributed as source code. It is **not a hosted service**: there is no TCL Station account, subscription, server-side storage, or remote camera service. These terms describe use of the app; the [MIT License](LICENSE) governs copying, modification, and distribution of its code. Nothing here narrows the rights granted by that license.

## What the app does

The current build offers a weather panel, clock, a foreground on-tablet camera preview, shortcuts into installed photo/timer/music apps, short beep and spoken-reply tests, **Speak** push-to-talk through an installed Android recognizer, optional continuously listening local Vosk recognition, and an optional Android Home-app role. Hands-free must be explicitly enabled; it requests microphone permission then runs as a foreground service with a notification Stop action. While it runs, the tablet microphone captures audio even when the screen is off or another app is visible. It recognizes “Hey Station” or “Station” command prefixes locally; “Hey Station” alone gets an acknowledgment and admits one bounded command in the next 35 seconds without repeating the prefix. This is not a guaranteed dedicated wake-word engine. Time and cached-weather replies can work outside the foreground app; other commands ask you to open Station. The listener does not start at boot, and there is no in-app audio/video recording or remote camera feed. The Hermes panel, EQ controls, and remote commands are not implemented. Ten existing actions have 100 documented short phrase variants, not 100 distinct capabilities. An optional alternate offline English voice is selectable from the Studio Audio card; it can be switched back to the original. Features can fail or change while this prototype develops; the user reported hands-free working in the room, but recognition of every new phrase and the alternate voice's subjective quality have not been established.

## Your responsibilities

You control installation, Android's reversible choice of Home app, and device permissions. Enable continuous listening only where ambient microphone capture is appropriate and you have the necessary consent; use the live camera and speech recognition with respect for others' privacy. Stop hands-free with its in-app control or notification, or revoke microphone permission in Android settings. Continuous capture uses battery, may be interrupted by Android, and may mishear commands or activate falsely. The separate Speak recognizer may use the network; third-party recognizer, TTS, and photo apps may have different behavior. Restore your previous launcher through Android Home-app settings if Station is unsuitable. Keep the tablet physically secure. This prototype is not a monitored security system, emergency alert service, or record of an event.

Weather information comes from Open-Meteo's free API and is subject to its own terms, including its non-commercial-use conditions.[1] Music and manual camera/timer actions open other apps or websites governed by their own terms. TCL Station is not affiliated with TCL, Amazon, Google, YouTube, or Open-Meteo.

## Availability, warranty, and liability

The software is provided **as is**, without a promise of continuous operation, reliable speech recognition, accurate weather, uninterrupted preview, or fitness for a security-critical purpose. The warranty and liability provisions of the [MIT License](LICENSE) apply to the software, to the extent permitted by applicable law. Bundled Vosk, model, and native-library licenses are listed separately in [Third-party notices](THIRD_PARTY_NOTICES.md). Nothing in these terms limits rights that cannot legally be waived.

## Privacy, changes, and contact

See the [Privacy Policy](PRIVACY.md) for live camera handling, old-clip cleanup, local microphone recognition, cached weather, external speech-provider handling, launcher choice, and third-party weather requests. Material changes to these documents will be committed in this public repository. To ask a non-sensitive question, open a GitHub issue on the repository; **do not post photos, addresses, credentials, voice transcripts, or other private data in public issues**. No paid support or response time is promised.

## Sources

[1] https://open-meteo.com/en/terms — Open-Meteo Terms and Privacy
