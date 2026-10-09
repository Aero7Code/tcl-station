package com.aero.tclstation;

public final class HandsFreeGateTest {
    private static void expect(HandsFreeGate gate, String phrase, long millis, VoiceCommand.Action action, int seconds) {
        VoiceCommand result = gate.accept(phrase, millis);
        if (result.action != action || result.seconds != seconds)
            throw new AssertionError(phrase + " => " + result.action + "/" + result.seconds + ", expected " + action + "/" + seconds);
    }

    public static void main(String[] args) {
        HandsFreeGate gate = new HandsFreeGate();
        if (gate.listeningForCommand(1000)) throw new AssertionError("No wake means no active command window");
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
        expect(hey, "show weather", 9000, VoiceCommand.Action.WEATHER, 0); // Multiple follow-ups per wake.
        expect(hey, "hey station", 10000, VoiceCommand.Action.WAKE, 0);
        expect(hey, "show weather", 54999, VoiceCommand.Action.WEATHER, 0); // Within 45 seconds.
        expect(hey, "hey station", 60000, VoiceCommand.Action.WAKE, 0);
        expect(hey, "show weather", 105000, VoiceCommand.Action.WEATHER, 0); // At the boundary.
        expect(hey, "hey station", 110000, VoiceCommand.Action.WAKE, 0);
        if (!hey.listeningForCommand(155000) || hey.listeningForCommand(155001))
            throw new AssertionError("Command feedback must obey the fixed 45-second wake window");
        expect(hey, "show weather", 155001, VoiceCommand.Action.UNKNOWN, 0); // Wake expired.
        expect(hey, "hey station", 156000, VoiceCommand.Action.WAKE, 0);
        expect(hey, "stop listening", 156001, VoiceCommand.Action.STOP_LISTENING, 0);
        HandsFreeGate apostrophe = new HandsFreeGate();
        expect(apostrophe, "Hey Station what's the weather", 1000, VoiceCommand.Action.WEATHER, 0);
        expect(apostrophe, "Hey Station how's the weather", 7000, VoiceCommand.Action.WEATHER, 0);
        expect(apostrophe, "Hey Station what's the time", 13000, VoiceCommand.Action.TIME, 0);
        expect(hey, "hey stationary show weather", 157000, VoiceCommand.Action.UNKNOWN, 0);
        expect(hey, "my hey station show weather", 158000, VoiceCommand.Action.UNKNOWN, 0);
        for (java.util.Map.Entry<String, VoiceCommand> item : VoiceCommand.catalog().entrySet()) {
            HandsFreeGate sample = new HandsFreeGate();
            expect(sample, "Hey Station " + item.getKey(), 1000, item.getValue().action, item.getValue().seconds);
            expect(sample, "Hey Station", 2000, VoiceCommand.Action.WAKE, 0);
            expect(sample, item.getKey(), 8000, item.getValue().action, item.getValue().seconds);
        }
        System.out.println("PASS local wake-prefix, explicit stop, and duplicate suppression");
    }
}
