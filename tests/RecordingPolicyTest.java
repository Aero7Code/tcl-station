package com.aero.tclstation;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

public final class RecordingPolicyTest {
    private static int checks;
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
        checks++;
    }
    private static File put(Path dir, String name, int length) throws Exception {
        File file = dir.resolve(name).toFile();
        Files.write(file.toPath(), new byte[length]);
        return file;
    }
    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("recording-policy-");
        try {
            File oldest = put(dir, "station-001.mp4", 4);
            File middle = put(dir, "station-002.mp4", 4);
            File active = put(dir, "station-003.mp4", 4);
            File unrelated = put(dir, "manual-video.mp4", 30);
            File partial = put(dir, "station-004.partial", 5);
            RecordingPolicy policy = new RecordingPolicy(2, 9);
            policy.prune(dir.toFile(), active);
            check(!oldest.exists(), "oldest completed segment evicted first");
            check(middle.exists() && active.exists(), "active segment protected");
            check(unrelated.exists() && partial.exists(), "unrelated and partial files untouched");
            File newer = put(dir, "station-005.mp4", 8);
            policy.prune(dir.toFile(), active);
            check(!middle.exists() && !newer.exists(), "caps include active size, even if newest completed is evicted");
            check(active.exists(), "active never deleted by policy");
            check(policy.isManagedFile(new File("station-20261008-0001.mp4")), "managed segment recognized");
            check(!policy.isManagedFile(new File("station-001.mp4.bak")), "only exact extension recognized");
            check(!policy.isManagedFile(new File("station-../secret.mp4")), "invalid names rejected");
            System.out.println("RecordingPolicyTest: " + checks + " checks passed");
        } finally {
            for (File file : dir.toFile().listFiles()) file.delete();
            Files.delete(dir);
        }
    }
}
