package com.aero.tclstation;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class LegacyClipCleanupTest {
    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory(Paths.get(System.getenv("TMPDIR")), "legacy-clips-");
        try {
            Path clip = dir.resolve("station-1234567890123-001.mp4");
            Path partial = dir.resolve("station-1234567890124-002.partial");
            Path other = dir.resolve("notes.txt");
            Path lookalike = dir.resolve("station-1.mp4");
            Path wrongSequence = dir.resolve("station-1234567890123-1.mp4");
            Files.write(clip, new byte[]{1, 2});
            Files.write(partial, new byte[]{3});
            Files.write(other, new byte[]{4});
            Files.write(lookalike, new byte[]{5});
            Files.write(wrongSequence, new byte[]{6});
            LegacyClipCleanup.remove(dir.toFile());
            if (Files.exists(clip) || Files.exists(partial)) throw new AssertionError("old camera footage remains");
            if (!Files.exists(other)) throw new AssertionError("unrelated data was deleted");
            if (!Files.exists(lookalike) || !Files.exists(wrongSequence)) throw new AssertionError("lookalike unrelated data was deleted");
            LegacyClipCleanup.remove(dir.toFile());
            System.out.println("PASS legacy app recordings purged; unrelated files kept; repeat is safe");
        } finally {
            try (java.util.stream.Stream<Path> paths = Files.list(dir)) { paths.forEach(p -> p.toFile().delete()); }
            dir.toFile().delete();
        }
    }
}
