package com.aero.tclstation;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;

import java.util.Locale;
import java.util.Set;

/** Optional alternate voice; only select one the engine marks installed and offline. */
final class StationVoiceProfile {
    private static final String PREFERENCE = "soft_voice";
    private static final String ALTERNATE = "en-us-x-sfg-local";

    static boolean softer(Context context) {
        return context.getSharedPreferences("station", Context.MODE_PRIVATE).getBoolean(PREFERENCE, false);
    }

    static boolean toggle(Context context) {
        boolean enabled = !softer(context);
        context.getSharedPreferences("station", Context.MODE_PRIVATE).edit().putBoolean(PREFERENCE, enabled).apply();
        return enabled;
    }

    /** Returns true only when an installed offline alternate was selected. */
    static boolean apply(Context context, TextToSpeech speech) {
        speech.setLanguage(Locale.US); // Restore the original locale voice when switching back.
        if (!softer(context)) return false;
        boolean selected = false;
        Set<Voice> voices = speech.getVoices();
        if (voices != null) for (Voice voice : voices) {
            Set<String> features = voice.getFeatures();
            if (ALTERNATE.equals(voice.getName()) && Locale.US.equals(voice.getLocale())
                    && !voice.isNetworkConnectionRequired()
                    && features != null && !features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)) {
                if (speech.setVoice(voice) == TextToSpeech.SUCCESS && speech.getVoice() != null
                        && ALTERNATE.equals(speech.getVoice().getName())) selected = true;
                else speech.setLanguage(Locale.US);
                break;
            }
        }
        return selected;
    }
}
