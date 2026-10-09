"""Static guardrails for prototype TTS voice privacy and upgrade behavior."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
profile = (root / "src/com/aero/tclstation/StationVoiceProfile.java").read_text()
activity = (root / "src/com/aero/tclstation/MainActivity.java").read_text()
assert "getBoolean(PREFERENCE, false)" in profile, "Existing users must retain their original TTS voice"
assert "TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED" in profile, "Do not select a voice needing data download"
assert "!voice.isNetworkConnectionRequired()" in profile, "Do not automatically choose a network TTS voice"
assert "voice.getFeatures()" in profile, "Inspect voice availability features"
assert "Softer voice selected." not in activity, "Do not claim the alternate voice works before checking"
assert "setSpeechRate(" not in profile and "setPitch(" not in profile, "Opt-out must preserve system speech rate and pitch"
print("PASS opt-in alternate voice: default/rate/pitch unchanged, no network/download voice, no premature success claim")
