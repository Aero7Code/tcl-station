package com.aero.tclstation;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/** Bounded, text-free diagnostic events; never accept a transcript or free-form message. */
final class StationDiagnosticLog {
    static final long MAX_BYTES = 32 * 1024;
    enum Event {
        ACTIVITY_STARTED, ACTIVITY_STOPPED, SERVICE_STARTED, SERVICE_STOPPED,
        MIC_STARTED, MIC_STOPPED, VOICE_BIND_REQUEST, VOICE_BIND_OK, VOICE_BIND_FAILED,
        VOICE_ENQUEUE_ACCEPTED, VOICE_ENQUEUE_FAILED, VOICE_STARTED, VOICE_FINISHED,
        VOICE_ERROR, VOICE_WATCHDOG_TIMEOUT, HANDS_FREE_DISABLED,
        ACTIVITY_VOICE_BIND_REQUEST, ACTIVITY_VOICE_BIND_OK, ACTIVITY_VOICE_BIND_FAILED,
        ACTIVITY_VOICE_ENQUEUE_ACCEPTED, ACTIVITY_VOICE_ENQUEUE_FAILED,
        ACTIVITY_VOICE_STARTED, ACTIVITY_VOICE_FINISHED, ACTIVITY_VOICE_ERROR,
        ACTIVITY_VOICE_WATCHDOG_TIMEOUT, TTS_STOP_UNCONFIRMED, PROCESS_EXIT_SAFETY
    }

    static synchronized boolean record(File directory, Event event, long epochMillis, long elapsedMillis, int pid) {
        if (directory == null || event == null || (!directory.isDirectory() && !directory.mkdirs())) return false;
        byte[] line = (Instant.ofEpochMilli(epochMillis) + " pid=" + pid + " elapsed_ms=" + elapsedMillis
            + " event=" + event.name() + "\n").getBytes(StandardCharsets.UTF_8);
        File current = new File(directory, "station-diagnostics.log");
        File previous = new File(directory, "station-diagnostics.log.1");
        if (current.length() + line.length > MAX_BYTES) {
            if (previous.exists() && !previous.delete()) return false;
            if (current.exists() && !current.renameTo(previous)) return false;
        }
        try (FileOutputStream out = new FileOutputStream(current, true)) {
            out.write(line);
            out.getFD().sync(); // The last event must survive an intentional safety shutdown.
            return true;
        } catch (IOException | SecurityException ignored) {
            return false; // Diagnostics must never take down Station.
        }
    }
}
