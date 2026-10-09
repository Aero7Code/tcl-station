package com.aero.tclstation;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Deletes only completed, app-named segments in the supplied app-private directory. */
public final class RecordingPolicy {
    private final int maxSegments;
    private final long maxBytes;

    public RecordingPolicy(int maxSegments, long maxBytes) {
        if (maxSegments < 1 || maxBytes < 1) throw new IllegalArgumentException("positive limits required");
        this.maxSegments = maxSegments;
        this.maxBytes = maxBytes;
    }

    public boolean isManagedFile(File file) {
        return file != null && file.getName().matches("station-[0-9]+(?:-[0-9]+)?\\.mp4");
    }

    /** Evict oldest finalized segments until both limits hold; never delete active or foreign files. */
    public void prune(File directory, File active) {
        File[] files = directory.listFiles();
        if (files == null) throw new IllegalStateException("Cannot list recording directory");
        List<File> segments = new ArrayList<>();
        long bytes = 0;
        for (File file : files) {
            if (file.isFile() && isManagedFile(file)) {
                segments.add(file);
                bytes += file.length();
            }
        }
        segments.sort(Comparator.comparing(File::getName));
        int count = segments.size();
        for (File file : segments) {
            if (count <= maxSegments && bytes <= maxBytes) break;
            if (active != null && file.equals(active)) continue;
            long length = file.length();
            if (!file.delete()) throw new IllegalStateException("Cannot delete old segment: " + file.getName());
            bytes -= length;
            count--;
        }
        if (count > maxSegments || bytes > maxBytes) throw new IllegalStateException("Active segment exceeds recording budget");
    }
}
