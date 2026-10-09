package com.aero.tclstation;

public final class VoiceCommandTest {
    private static void expect(String phrase, VoiceCommand.Action expected, int seconds) {
        VoiceCommand cmd = VoiceCommand.parse(phrase);
        if (cmd.action != expected || cmd.seconds != seconds)
            throw new AssertionError(phrase + " => " + cmd.action + "/" + cmd.seconds + ", expected " + expected + "/" + seconds);
    }

    public static void main(String[] args) {
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
        System.out.println("PASS 19 deterministic voice command cases; no recording command");
    }
}
