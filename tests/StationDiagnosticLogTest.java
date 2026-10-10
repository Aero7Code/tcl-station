package com.aero.tclstation;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public final class StationDiagnosticLogTest {
    private static void check(boolean yes, String message) { if (!yes) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        File directory = Files.createTempDirectory(new File("build/test-classes").toPath(), "station-diag-").toFile();
        try {
            check(StationDiagnosticLog.record(directory, StationDiagnosticLog.Event.SERVICE_STARTED, 1000, 200, 123), "first event written");
            String first = new String(Files.readAllBytes(new File(directory, "station-diagnostics.log").toPath()), StandardCharsets.UTF_8);
            check(first.contains("SERVICE_STARTED") && first.contains("pid=123"), "event and process ID recorded");
            check(!first.contains("transcript") && !first.contains("coordinate"), "no free-text fields");
            for (int i = 0; i < 3000; ++i)
                check(StationDiagnosticLog.record(directory, StationDiagnosticLog.Event.VOICE_BIND_REQUEST, 1000 + i, 200 + i, 123), "bounded append");
            File active = new File(directory, "station-diagnostics.log");
            File previous = new File(directory, "station-diagnostics.log.1");
            check(active.length() <= StationDiagnosticLog.MAX_BYTES, "current file bounded");
            check(previous.exists() && previous.length() <= StationDiagnosticLog.MAX_BYTES, "one prior file retained");
            check(StationDiagnosticLog.record(directory, StationDiagnosticLog.Event.TTS_STOP_UNCONFIRMED, 5000, 4000, 124), "fatal event persisted");
            check(new String(Files.readAllBytes(active.toPath()), StandardCharsets.UTF_8).contains("TTS_STOP_UNCONFIRMED"), "fatal event retrievable");
            System.out.println("PASS StationDiagnosticLogTest");
        } finally {
            for (File file : directory.listFiles()) file.delete();
            directory.delete();
        }
    }
}
