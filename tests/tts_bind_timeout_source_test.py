"""Cold TTS binding timeout must not kill an app that never enqueued audio."""
from pathlib import Path
activity = Path('src/com/aero/tclstation/MainActivity.java').read_text()
watch = activity.split('private void watchActivitySpeech(String id)', 1)[1].split('private boolean applyVoiceChoice()', 1)[0]
assert 'if (!speechReady && id.equals(pendingSpeechId)) {' in watch
unbound = watch.split('if (!speechReady && id.equals(pendingSpeechId)) {', 1)[1].split('return;', 1)[0]
assert '++speechGeneration;' in unbound and 'speech.shutdown()' in unbound
assert 'ListeningService.cancelSpokenResponses(this)' in unbound
assert 'speech.stop()' not in unbound and 'killProcess' not in unbound
assert watch.index('if (!speechReady && id.equals(pendingSpeechId)) {') < watch.index('speech.stop()')
teardown = activity.split('@Override protected void onDestroy()', 1)[1]
assert 'speech == null || !speechReady || speech.stop() == TextToSpeech.SUCCESS' in teardown
assert teardown.index('++speechGeneration;') < teardown.index('speech.shutdown()')
assert teardown.index('pendingSpeech = null;') < teardown.index('speech.shutdown()')
print('PASS unbound TTS never kills Station or resumes microphone before safety stop')
