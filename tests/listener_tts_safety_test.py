"""A stalled speech output must fail closed, not reopen the microphone over TTS."""
from pathlib import Path
source = (Path(__file__).resolve().parent.parent /
          'src/com/aero/tclstation/ListeningService.java').read_text()
start = source.index('private String deferResponse(Object owner, String prefix)')
end = source.index('private void finishResponse(Object owner, String id)', start)
block = source[start:end]
assert 'speechHold.isHeld(id)' in block and 'stopMicrophone()' in block
assert 'stopSelf()' in block, 'TTS timeout must shut down the listener'
assert 'scheduleResume(15000)' not in block, 'TTS timeout can restart mic while TTS speaks'
print('PASS stalled TTS cannot automatically reactivate microphone')
