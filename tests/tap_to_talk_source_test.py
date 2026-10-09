"""Guard against regressing Speak to Android's separate/cloud-capable recognizer."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
activity = (root / "src/com/aero/tclstation/MainActivity.java").read_text()
service = (root / "src/com/aero/tclstation/ListeningService.java").read_text()
loader = root / "src/com/aero/tclstation/StationModelLoader.java"

assert "RecognizerIntent" not in activity and "ACTION_RECOGNIZE_SPEECH" not in activity
assert "SpeechService" in activity and "RecognitionListener" in activity
assert "StationModelLoader.load(this," in activity
assert "StationModelLoader.load(this," in service
assert '"model-en-us"' in loader.read_text() and '"model"' in loader.read_text()
assert "ListeningService.pauseForPushToTalk()" in activity
assert "speechHold.pauseForPushToTalk(); // Persist before a service exists" in service
assert "speechHold.resumeAfterPushToTalk();" in service
assert "ListeningService.resumeAfterPushToTalk()" in activity
assert "tapMicrophone.cancel()" in activity and "tapRecognizer.close()" in activity
assert "onPause()" in activity and "stopLocalInput" in activity
assert "Manifest.permission.RECORD_AUDIO" in activity
print("PASS Speak and hands-free share local Vosk model source and independent control")
