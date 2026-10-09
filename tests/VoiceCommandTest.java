package com.aero.tclstation;

public final class VoiceCommandTest {
    private static void expect(String phrase, VoiceCommand.Action expected, int seconds) {
        VoiceCommand cmd = VoiceCommand.parse(phrase);
        if (cmd.action != expected || cmd.seconds != seconds)
            throw new AssertionError(phrase + " => " + cmd.action + "/" + cmd.seconds + ", expected " + expected + "/" + seconds);
    }

    public static void main(String[] args) throws Exception {
        expect("What's the weather?", VoiceCommand.Action.WEATHER, 0);
        expect("Show me the weather", VoiceCommand.Action.WEATHER, 0);
        expect("What time is it?", VoiceCommand.Action.TIME, 0);
        expect("Open live camera", VoiceCommand.Action.CAMERA, 0);
        expect("Close camera", VoiceCommand.Action.CLOSE_CAMERA, 0);
        expect("Set a timer for five minutes", VoiceCommand.Action.SET_TIMER, 300);
        expect("Set timer for 30 seconds", VoiceCommand.Action.SET_TIMER, 30);
        expect("Set timer for 120 minutes", VoiceCommand.Action.SET_TIMER, 7200);
        expect("Open timers", VoiceCommand.Action.TIMERS, 0);
        expect("Show alarms", VoiceCommand.Action.ALARMS, 0);
        expect("Play music", VoiceCommand.Action.MUSIC, 0);
        expect("Go home", VoiceCommand.Action.HOME, 0);
        expect("Help", VoiceCommand.Action.HELP, 0);
        expect("Record a video", VoiceCommand.Action.UNKNOWN, 0);
        expect("Stop recording", VoiceCommand.Action.UNKNOWN, 0);
        expect("Set timer for zero minutes", VoiceCommand.Action.UNKNOWN, 0);
        expect("Set timer for 100000 minutes", VoiceCommand.Action.UNKNOWN, 0);
        expect("Set timer for 4611686018427387909 minutes", VoiceCommand.Action.UNKNOWN, 0);
        expect("Call my contacts", VoiceCommand.Action.UNKNOWN, 0);
        expect("current weather", VoiceCommand.Action.WEATHER, 0);
        expect("Denver time", VoiceCommand.Action.TIME, 0);
        expect("turn on camera", VoiceCommand.Action.CAMERA, 0);
        expect("hide camera", VoiceCommand.Action.CLOSE_CAMERA, 0);
        expect("set a three minute timer", VoiceCommand.Action.SET_TIMER, 180);
        expect("my timers", VoiceCommand.Action.TIMERS, 0);
        expect("my alarms", VoiceCommand.Action.ALARMS, 0);
        expect("launch YouTube Music", VoiceCommand.Action.MUSIC, 0);
        expect("back to home", VoiceCommand.Action.HOME, 0);
        expect("stop listening now", VoiceCommand.Action.STOP_LISTENING, 0);
        if (VoiceCommand.catalog().size() != 100) throw new AssertionError("Expected exactly 100 curated phrases");
        java.util.Map<VoiceCommand.Action, Integer> counts = new java.util.EnumMap<>(VoiceCommand.Action.class);
        for (java.util.Map.Entry<String, VoiceCommand> entry : VoiceCommand.catalog().entrySet()) {
            VoiceCommand expected = entry.getValue();
            String phrase = entry.getKey();
            expect(phrase, expected.action, expected.seconds);
            HandsFreeGate prefixed = new HandsFreeGate();
            VoiceCommand oneShot = prefixed.accept("Hey Station " + phrase, 1000);
            if (oneShot.action != expected.action || oneShot.seconds != expected.seconds)
                throw new AssertionError("Prefixed phrase failed: " + phrase);
            HandsFreeGate followUp = new HandsFreeGate();
            followUp.accept("Hey Station", 1000);
            VoiceCommand second = followUp.accept(phrase, 2000);
            if (second.action != expected.action || second.seconds != expected.seconds)
                throw new AssertionError("Follow-up failed: " + phrase);
            counts.put(expected.action, counts.getOrDefault(expected.action, 0) + 1);
        }
        if (counts.size() != 10 || counts.values().stream().anyMatch(n -> n != 10))
            throw new AssertionError("Expected ten phrases per supported action: " + counts);
        java.util.Set<String> documented = new java.util.HashSet<>();
        for (String line : java.nio.file.Files.readAllLines(java.nio.file.Paths.get("VOICE_COMMANDS.md"))) {
            if (line.startsWith("- “")) {
                int end = line.indexOf('”', 3);
                if (end < 0 || !documented.add(line.substring(3, end)))
                    throw new AssertionError("Duplicate or malformed documented phrase: " + line);
            }
        }
        if (!documented.equals(VoiceCommand.catalog().keySet()))
            throw new AssertionError("Documentation and runtime catalog differ");
        System.out.println("PASS 100 documented phrases across ten supported actions, prefix and follow-up; no recording command");
    }
}
