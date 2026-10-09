# Privacy Policy — TCL Station

Effective: October 8, 2026

**Scope.** This policy covers the open-source TCL Station Android app in this repository, maintained under the GitHub account **Aero7Code**. The app has no account system, advertising SDK, analytics service, or TCL Station cloud. This describes the code currently published, not future integrations or unrelated apps launched from the dashboard.

## Information used by the app

- **Weather:** The app sends configured latitude/longitude to the Open-Meteo forecast API over HTTPS when weather loads or you refresh it. The default repository configuration uses an example city-center point; a builder can provide a private local override. The app does not request GPS/device location permission. Open-Meteo receives the request and can see its network source (including an IP address); its own terms/privacy policy explains its handling of API requests.[1] We do not operate that service.
- **Camera recordings:** If you grant Android camera permission and explicitly tap **Start local recording**, the experimental service records video **without microphone audio** to app-private storage on the tablet, with a visible foreground notification. Tap **Stop recording** or the notification action to stop. Completed clips are automatically pruned to at most 20 files and within a storage budget; an unfinished clip can be discarded after interruption. There is no automatic upload or Hermes remote access in this build. The prototype has not yet verified clip playback and should not be your sole security recording system.
- **Other actions:** **Take photo** and **Record video manually** launch another installed camera app; those files are controlled by that app, not this recorder. Timer and alarm buttons launch Android's clock handler. **Open YouTube Music** hands off to an installed app or browser; accounts, cookies, and playback on those services are governed by their providers' policies. The short test beep does not record audio.
- **Local status:** The recorder saves its last reported state/detail in Android app preferences. No identifier, media, or status is sent to a TCL Station server. Android, the device manufacturer, and other apps may process data according to their own settings and policies.

## Storage and deletion

Recordings are kept in the app's private storage rather than committed to this repository. Android device access controls apply, but this app adds no independent encryption. You can clear the app's storage or uninstall it to remove its app-private clips and status; Android/system backups may have separate behavior, although this app disables its own standard backup. Clips created by a *different* camera app must be deleted through that app or Android storage. Do not share clips or detailed location configuration in public GitHub issues.

## Sharing and changes

TCL Station does not sell personal data. Its weather request goes to Open-Meteo, and tapping external shortcuts may send you to independent providers. A future host, Hermes, sync, or cloud feature would require a policy and code update before being described as available. Changes to this policy will be published in the repository. For non-sensitive privacy questions, use GitHub issues under **Aero7Code/tcl-station**; do not post personal information there.

## Sources

[1] https://open-meteo.com/en/terms — Open-Meteo Terms and Privacy
