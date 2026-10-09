package com.aero.tclstation;

import java.io.File;
import java.io.IOException;

/** Removes only clips made by the retired prototype recorder on app upgrade. */
public final class LegacyClipCleanup {
    private LegacyClipCleanup() { }

    public static void remove(File directory) throws IOException {
        if (!directory.exists()) return;
        File[] files = directory.listFiles();
        if (files == null) throw new IOException("Cannot inspect old recording directory");
        for (File file : files) {
            if (file.isFile() && file.getName().matches("station-[0-9]{13}-[0-9]{3}\\.(?:mp4|partial)")) {
                if (!file.delete()) throw new IOException("Could not remove old recording");
            }
        }
        File[] remaining = directory.listFiles();
        if (remaining != null && remaining.length == 0) directory.delete();
    }
}
