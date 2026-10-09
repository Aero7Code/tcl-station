# Privacy Policy — TCL Station

Effective: October 9, 2026

**Scope.** This policy covers the open-source TCL Station Android app in this repository, maintained under the GitHub account **Aero7Code**. The app has no account system, advertising SDK, analytics service, or TCL Station cloud. This describes the code currently published, not future integrations or unrelated apps launched from the dashboard.

## Information used by the app

- **Weather:** The app sends configured latitude/longitude to the Open-Meteo forecast API over HTTPS when weather loads or you refresh it. The default repository configuration uses an example city-center point; a builder can provide a private local override. The app does not request GPS/device location permission. Open-Meteo receives the request and can see its network source (including an IP address); its own terms/privacy policy explains its handling of API requests.[1] We do not operate that service.
- **Live camera:** Android camera permission is requested when you tap **Open live camera**. The app displays front or back camera frames on the tablet screen while the preview is open. It does not record, save, transmit, or analyze these frames; the preview closes when you leave the app. Android and system-level components may have their own camera-access indicators and behavior.
- **Other actions:** **Take photo** launches another installed camera app; photos are controlled by that app, not TCL Station. Timer and alarm buttons launch Android's clock handler. **Open YouTube Music** hands off to an installed app or browser; accounts, cookies, and playback on those services are governed by their providers' policies. The short test beep does not record audio.
- **Local status:** No camera status, identifiers, or media are sent to a TCL Station server. Android, the device manufacturer, and other apps may process data according to their own settings and policies.

## Storage and deletion

The current build does not create camera recordings. On startup after an upgrade, it attempts to delete only the old recorder's matching `station-*.mp4` and `.partial` clips in its former app-private `security-recordings` directory; unrelated files are left alone. If cleanup cannot complete, clear TCL Station's app storage or uninstall it to remove app-private data. The app disables standard Android backup, but independently created copies or system-level backups are outside its control. Photos created by a *different* camera app must be managed through that app or Android storage. Do not share photos or detailed location configuration in public GitHub issues.

## Sharing and changes

TCL Station does not sell personal data. Its weather request goes to Open-Meteo, and tapping external shortcuts may send you to independent providers. A future host, Hermes, sync, or cloud feature would require a policy and code update before being described as available. Changes to this policy will be published in the repository. For non-sensitive privacy questions, use GitHub issues under **Aero7Code/tcl-station**; do not post personal information there.

## Sources

[1] https://open-meteo.com/en/terms — Open-Meteo Terms and Privacy
