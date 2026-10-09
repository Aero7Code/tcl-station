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
        System.out.println("PASS local wake-prefix, explicit stop, and duplicate suppression");
    }
}
