package com.aero.tclstation;

public final class HandsFreeGateTest {
    private static void expect(HandsFreeGate gate, String phrase, long millis, VoiceCommand.Action action, int seconds) {
        VoiceCommand result = gate.accept(phrase, millis);
        if (result.action != action || result.seconds != seconds)
            throw new AssertionError(phrase + " => " + result.action + "/" + result.seconds + ", expected " + action + "/" + seconds);
    }

    public static void main(String[] args) {
        HandsFreeGate gate = new HandsFreeGate();
        expect(gate, "show weather", 1000, VoiceCommand.Action.UNKNOWN, 0);
        expect(gate, "my station show weather", 1000, VoiceCommand.Action.UNKNOWN, 0);
        expect(gate, "station show weather", 1000, VoiceCommand.Action.WEATHER, 0);
        expect(gate, "station show weather", 2000, VoiceCommand.Action.UNKNOWN, 0);
        expect(gate, "station set timer for five minutes", 2100, VoiceCommand.Action.SET_TIMER, 300);
        expect(gate, "station show weather", 7000, VoiceCommand.Action.WEATHER, 0);
        expect(gate, "station record video", 8000, VoiceCommand.Action.UNKNOWN, 0);
        expect(gate, "station stop listening", 8100, VoiceCommand.Action.STOP_LISTENING, 0);
        expect(gate, "station stop listening", 9000, VoiceCommand.Action.UNKNOWN, 0);
        HandsFreeGate hey = new HandsFreeGate();
        expect(hey, "Hey Station what time is it?", 1000, VoiceCommand.Action.TIME, 0);
        expect(hey, "Hey, Station show weather!", 2000, VoiceCommand.Action.WEATHER, 0);
        expect(hey, "station show weather", 2100, VoiceCommand.Action.UNKNOWN, 0); // Same command, either prefix.
        expect(hey, "Hey Station stop listening", 3000, VoiceCommand.Action.STOP_LISTENING, 0);
        expect(hey, "hey station", 4000, VoiceCommand.Action.WAKE, 0);
        expect(hey, "what time is it", 8000, VoiceCommand.Action.TIME, 0);
        expect(hey, "show weather", 9000, VoiceCommand.Action.UNKNOWN, 0); // One follow-up per wake.
        expect(hey, "hey station", 10000, VoiceCommand.Action.WAKE, 0);
        expect(hey, "show weather", 25001, VoiceCommand.Action.UNKNOWN, 0); // Wake expired.
        expect(hey, "hey station", 26000, VoiceCommand.Action.WAKE, 0);
        expect(hey, "stop listening", 26001, VoiceCommand.Action.STOP_LISTENING, 0);
        expect(hey, "hey stationary show weather", 27000, VoiceCommand.Action.UNKNOWN, 0);
        expect(hey, "my hey station show weather", 28000, VoiceCommand.Action.UNKNOWN, 0);
        System.out.println("PASS local wake-prefix, explicit stop, and duplicate suppression");
    }
}
