"""Static guardrails for prototype TTS voice privacy and upgrade behavior."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
profile = (root / "src/com/aero/tclstation/StationVoiceProfile.java").read_text()
activity = (root / "src/com/aero/tclstation/MainActivity.java").read_text()
assert 'PREFERENCE = "offline_model_voice_opt_in"' in profile, "Legacy alternate must not opt users into a third-party engine"
assert "getBoolean(PREFERENCE, false)" in profile, "Existing users start on the system engine"
assert "getBoolean(\"soft_voice\"" not in profile, "Legacy selection must not migrate silently"
service = (root / "src/com/aero/tclstation/ListeningService.java").read_text()
manifest = (root / "AndroidManifest.xml").read_text()
assert 'com.k2fsa.sherpa.onnx.tts.engine' in profile, "A real model-backed engine is required"
assert 'android.intent.action.TTS_SERVICE' in manifest, "Android 11+ must be able to see TTS engines"
assert 'StationVoiceProfile.create(this,' in activity and 'StationVoiceProfile.create(this,' in service, "Both paths must select the model engine"
assert 'MODEL_VOICE.equals(speech.getVoice().getName())' in profile, "Reject a system-engine fallback voice"
assert 'new TextToSpeech(new ModelOnlyTtsContext(context), callback, MODEL_ENGINE)' in profile, "Model path must bind only to the selected engine"
wrapper = (root / "src/com/aero/tclstation/ModelOnlyTtsContext.java").read_text()
assert "StationVoiceProfile.MODEL_ENGINE.equals(intent.getPackage())" in wrapper
assert "return permits(intent) && super.bindService(intent, connection, flags);" in wrapper
assert "return permits(intent) && super.bindService(intent, flags, executor, connection);" in wrapper
assert 'TextToSpeech.class.getDeclaredField("mCurrentEngine")' in profile, "Android's system TTS manager may bypass the bind wrapper"
assert 'if (MODEL_ENGINE.equals(engine)) return true;' in profile
assert 'Rejected non-model TTS engine' in profile
assert 'catch (ReflectiveOperationException | RuntimeException error)' in profile, "Inaccessible platform identity must fail closed"
assert 'StationVoiceProfile.apply(this, speech)' in activity and 'StationVoiceProfile.apply(this, voice)' in service
assert 'ListeningService.resetVoiceEngine()' in activity and 'static boolean resetVoiceEngine()' in service, "Voice switch must replace the background engine"
assert 'private void switchVoice()' in activity and 'speech.shutdown()' in activity, "Voice switch must replace the foreground engine"
assert 'if (model(context) && speech.setSpeechRate(1.08f) == TextToSpeech.ERROR)' in profile, "Increase only the offline model speed, not the system voice"
assert 'setPitch(' not in profile, "Sherpa TTS service ignores Android pitch requests; do not pretend to deepen by setting pitch"
assert "Softer voice selected." not in activity, "Do not claim a new voice without checking the actual engine"
print("PASS explicit model engine selection and fallback guard in both spoken paths")
