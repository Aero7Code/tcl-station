from pathlib import Path
root = Path(__file__).resolve().parents[1]
main = (root/'src/com/aero/tclstation/MainActivity.java').read_text()
service = (root/'src/com/aero/tclstation/ListeningService.java').read_text()
manifest = (root/'AndroidManifest.xml').read_text()
assert 'WeatherDetailDialog.show(' in main, 'weather second tap must show details'
assert 'case WEATHER:' in main and 'openWeatherPanel();' in main, 'foreground weather command must open panel'
assert 'ACTION_OPEN_WEATHER' in main and 'ACTION_OPEN_WEATHER' in service, 'background weather command must navigate to Station'
assert 'WeatherForecast.parse(' in main and '&daily=' in main, 'live daily data required'
assert 'weatherSummary = text(card, weatherDisplayText()' in main and 'say(weatherDisplayText()' in main, 'card and spoken weather must use freshness-gated summary'
assert 'scheduleWeatherExpiry();' in main and 'WeatherFreshness.canDisplay(' in main, 'visible forecast must expire at observation age and midnight'
assert '.remove("last_weather")' in main and '.putString("last_weather"' not in main, 'do not persist or resurrect an old weather summary'
refresh_start = main.split('private void loadWeather()', 1)[1].split('network.execute', 1)[0]
assert 'weatherDialog.dismiss()' in refresh_start and 'WeatherForecast.parse(null)' in refresh_start and 'WeatherDetailDialog.show(' in refresh_start, 'refresh must neutralize an open detail before network latency can make it stale'
assert 'boolean fresh = !weatherLoading && currentWeatherFresh()' in main, 'details opened during refresh must not show old snapshot'
completion = main.split('private void loadWeather()', 1)[1].split('weatherLoading = false;', 1)[1].split('});\n        });', 1)[0]
assert 'showWeatherDetail()' not in completion and 'WeatherDetailDialog.show(' in completion, 'failed refresh must render unavailable without automatically retrying forever'
assert 'START_STICKY' in service, 'opted-in listener should survive eligible system restarts'
assert 'HandsFreePreference.setEnabled(this, false)' in service, 'explicit stop must persist'
assert 'if (command.action == VoiceCommand.Action.STOP_LISTENING) {\n            HandsFreePreference.setEnabled(this, false);' in service, 'spoken Stop must clear the persistent opt-in'
assert 'if (active == this && speechHold.isHeld(id)) {\n                HandsFreePreference.setEnabled(this, false);' in service, 'TTS safety shutdown must clear the persistent opt-in'
assert 'HandsFreePreference.isEnabled(this)' in main, 'visible Station should recover opted-in listener'
assert 'android.permission.POST_NOTIFICATIONS' in manifest, 'Android 13+ notification consent must be declared'
assert 'android:targetSdkVersion="33"' in manifest, 'app must control Android 13 notification consent timing'
assert 'ListeningService.notificationsAvailable(this)' in main, 'visible opt-in must check notification and Stop visibility'
assert 'notificationsAvailable(this)' in service, 'foreground microphone must fail closed without a visible notification'
print('PASS weather and opt-in persistence wiring')
