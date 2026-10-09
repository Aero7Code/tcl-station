package com.aero.tclstation;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.ServiceConnection;
import android.speech.tts.TextToSpeech;

import java.util.concurrent.Executor;

/** Reject direct TTS fallback binds; the system-manager path also needs an engine-identity check. */
final class ModelOnlyTtsContext extends ContextWrapper {
    ModelOnlyTtsContext(Context base) { super(base); }

    private boolean permits(Intent intent) {
        return intent != null
            && TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE.equals(intent.getAction())
            && StationVoiceProfile.MODEL_ENGINE.equals(intent.getPackage())
            && (intent.getComponent() == null || StationVoiceProfile.MODEL_ENGINE.equals(intent.getComponent().getPackageName()));
    }

    @Override public boolean bindService(Intent intent, ServiceConnection connection, int flags) {
        return permits(intent) && super.bindService(intent, connection, flags);
    }

    @Override public boolean bindService(Intent intent, int flags, Executor executor, ServiceConnection connection) {
        return permits(intent) && super.bindService(intent, flags, executor, connection);
    }
}
