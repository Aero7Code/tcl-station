package com.aero.tclstation;

import android.content.Context;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;
import java.io.File;

/** App-specific, bounded event log readable by ADB; never writes audio or recognized words. */
final class StationDiagnostics {
    private static final String TAG = "TCLStationDiag";

    static void event(Context context, StationDiagnosticLog.Event event) {
        if (context == null || event == null) return;
        // Keep Logcat useful even if removable/emulated external storage is unavailable.
        Log.i(TAG, "event=" + event.name());
        try {
            File files = context.getExternalFilesDir(null);
            if (files != null) StationDiagnosticLog.record(new File(files, "diagnostics"), event,
                System.currentTimeMillis(), SystemClock.elapsedRealtime(), Process.myPid());
        } catch (RuntimeException ignored) {
            Log.w(TAG, "diagnostic_file_unavailable");
        }
    }

    private StationDiagnostics() { }
}
