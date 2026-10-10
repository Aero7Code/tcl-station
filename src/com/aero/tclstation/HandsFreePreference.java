package com.aero.tclstation;

import android.content.Context;

/** Explicit user choice, never inferred from an old install or microphone grant. */
final class HandsFreePreference {
    private static final String KEY = "keep_hands_free_on";
    static boolean isEnabled(Context context) {
        return context.getSharedPreferences("station", Context.MODE_PRIVATE).getBoolean(KEY, false);
    }
    static void setEnabled(Context context, boolean enabled) {
        context.getSharedPreferences("station", Context.MODE_PRIVATE).edit().putBoolean(KEY, enabled).apply();
    }
}
