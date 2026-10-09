package com.aero.tclstation;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.vosk.Model;
import org.vosk.android.StorageService;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Serializes Vosk's asset sync so Speak and hands-free never replace each other's files. */
final class StationModelLoader {
    interface Callback<T> { void onComplete(T value); }

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private StationModelLoader() { }

    static void load(Context context, Callback<Model> complete, Callback<IOException> failure) {
        Context app = context.getApplicationContext();
        WORKER.execute(() -> {
            try {
                // Each caller owns its own native Model; the disk copy is shared and synced once.
                Model model = new Model(StorageService.sync(app, "model-en-us", "model"));
                MAIN.post(() -> complete.onComplete(model));
            } catch (IOException error) {
                MAIN.post(() -> failure.onComplete(error));
            } catch (RuntimeException error) {
                MAIN.post(() -> failure.onComplete(new IOException("Could not load local speech model", error)));
            }
        });
    }
}
