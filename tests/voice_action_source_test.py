from pathlib import Path
root = Path(__file__).resolve().parents[1]
main = (root / 'src/com/aero/tclstation/MainActivity.java').read_text()
camera = (root / 'src/com/aero/tclstation/LiveCameraDialog.java').read_text()
listener = (root / 'src/com/aero/tclstation/ListeningService.java').read_text()
assert 'case CAMERA_FRONT:' in main and 'openLiveCamera(true)' in main
assert 'case CAMERA:' in main and 'openLiveCamera(false)' in main
assert 'void selectFacing(boolean front)' in camera
assert 'case SET_ALARM:' in main and 'AlarmClock.ACTION_SET_ALARM' in main
assert 'AlarmClock.EXTRA_HOUR' in main and 'AlarmClock.EXTRA_MINUTES' in main
assert 'AlarmClock.EXTRA_SKIP_UI, false' in main
for key in ('KEYCODE_MEDIA_PLAY', 'KEYCODE_MEDIA_PAUSE', 'KEYCODE_MEDIA_NEXT'):
    assert key in main
assert 'dispatchMediaKeyEvent' in main and 'isMusicActive()' in main
assert 'case PLAY_MEDIA:' in listener and 'case PAUSE_MEDIA:' in listener and 'case NEXT_MEDIA:' in listener
assert 'dispatchMediaKeyEvent' in listener and 'isMusicActive()' in listener
assert 'code != KeyEvent.KEYCODE_MEDIA_PLAY && !audio.isMusicActive()' in main
assert 'action != VoiceCommand.Action.PLAY_MEDIA && !audio.isMusicActive()' in listener
assert 'if (code == KeyEvent.KEYCODE_MEDIA_PLAY) openMusic();' not in main
assert 'gate.listeningForCommand(now)' in listener
assert 'showVoiceStatus("Heard: "' in listener, 'After wake, unknown phrases need ephemeral on-screen feedback'
assert '"Hands-free: On"' in main and 'stopSelf();' in listener
print('PASS Android alarm, camera-facing and media-key actions wired to the Station UI')
