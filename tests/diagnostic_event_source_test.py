"""Station diagnostic markers cover the watchdog-to-exit path without speech content."""
from pathlib import Path
src = Path('src/com/aero/tclstation')
service = (src / 'ListeningService.java').read_text()
activity = (src / 'MainActivity.java').read_text()
wrapper = (src / 'StationDiagnostics.java')
assert wrapper.exists(), 'Android event wrapper required'
wrapper_text = wrapper.read_text()
assert 'getExternalFilesDir(null)' in wrapper_text, 'app-specific external diagnostics retrievable over ADB'
assert 'StationDiagnosticLog.record' in wrapper_text, 'bounded event writer used'
assert 'VOICE_WATCHDOG_TIMEOUT' in service and 'TTS_STOP_UNCONFIRMED' in service, 'service failure chain logged'
assert 'ACTIVITY_VOICE_WATCHDOG_TIMEOUT' in activity and 'TTS_STOP_UNCONFIRMED' in activity, 'Activity failure chain logged'
assert 'PROCESS_EXIT_SAFETY' in service and 'PROCESS_EXIT_SAFETY' in activity, 'self-kill distinguishable from platform crash'
assert 'Heard:' not in wrapper_text and 'onResult' not in wrapper_text, 'diagnostic writer must not store speech'
print('PASS diagnostic event wiring')
