package com.aero.tclstation;

import android.content.Context;
import android.content.Intent;
import android.speech.tts.TextToSpeech;

import java.lang.reflect.Field;
import java.util.Locale;

/** Optional, real model-backed offline voice. Never mistake Android's fallback for it. */
final class StationVoiceProfile {
    private static final String PREFERENCE = "offline_model_voice_opt_in"; // New consent; alpha.5's soft_voice does not opt into another app.
    static final String MODEL_ENGINE = "com.k2fsa.sherpa.onnx.tts.engine";
    // Sherpa's locale-only TtsService reports LANG_AVAILABLE for eng; Android 12
    // normalizes that default voice to the BCP-47 name "en". This is an extra
    // compatibility check; ModelOnlyTtsContext enforces the engine at bind time.
    private static final String MODEL_VOICE = "en";

    static boolean model(Context context) {
        return context.getSharedPreferences("station", Context.MODE_PRIVATE).getBoolean(PREFERENCE, false);
    }

    static boolean toggle(Context context) {
        boolean enabled = !model(context);
        context.getSharedPreferences("station", Context.MODE_PRIVATE).edit().putBoolean(PREFERENCE, enabled).apply();
        return enabled;
    }

    /** The system path retains the user's default engine; the model path pins its own engine. */
    static TextToSpeech create(Context context, TextToSpeech.OnInitListener callback) {
        return model(context) ? new TextToSpeech(new ModelOnlyTtsContext(context), callback, MODEL_ENGINE)
                              : new TextToSpeech(context, callback);
    }

    static boolean apply(Context context, TextToSpeech speech) {
        if (model(context) && !boundToModelEngine(speech)) return false;
        if (speech.setLanguage(Locale.US) < 0 || !matches(context, speech)) return false;
        // Sherpa reads the Android speech-rate request; its TtsService does not
        // read pitch. Apply a small speed increase only to the model engine.
        if (model(context) && speech.setSpeechRate(1.08f) == TextToSpeech.ERROR) return false;
        return true;
    }

    static boolean matches(Context context, TextToSpeech speech) {
        if (!model(context)) return true;
        Intent intent = new Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE).setPackage(MODEL_ENGINE);
        if (context.getPackageManager().queryIntentServices(intent, 0).isEmpty()) return false;
        return boundToModelEngine(speech) && speech.getVoice() != null
            && MODEL_VOICE.equals(speech.getVoice().getName())
            && !speech.getVoice().isNetworkConnectionRequired();
    }

    private static boolean boundToModelEngine(TextToSpeech speech) {
        // Android 12's public three-argument constructor uses the system TTS manager,
        // which can fall back despite an explicit requested package. There is no public
        // bound-engine getter. Fail closed if this platform's internal identity cannot
        // be read; never pass a user's reply to an unverified provider.
        try {
            Field current = TextToSpeech.class.getDeclaredField("mCurrentEngine");
            current.setAccessible(true);
            String engine = (String) current.get(speech);
            if (MODEL_ENGINE.equals(engine)) return true;
            android.util.Log.w("TCLStation", "Rejected non-model TTS engine");
            return false;
        } catch (ReflectiveOperationException | RuntimeException error) {
            android.util.Log.w("TCLStation", "Cannot verify TTS engine identity; model voice disabled");
            return false;
        }
    }
}
