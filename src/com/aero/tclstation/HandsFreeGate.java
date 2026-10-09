package com.aero.tclstation;

import java.util.Locale;

/** A bounded follow-up after 'Hey Station', or an explicit prefixed command. */
final class HandsFreeGate {
    private static final long FOLLOW_UP_MS = 45000;
    private String lastPhrase = "";
    private long lastAt = -1;
    private long wokeAt = -1;

    boolean listeningForCommand(long elapsedMillis) {
        return wokeAt >= 0 && elapsedMillis >= wokeAt && elapsedMillis - wokeAt <= FOLLOW_UP_MS;
    }

    VoiceCommand accept(String finalTranscript, long elapsedMillis) {
        if (finalTranscript == null) return VoiceCommand.parse(null);
        String text = finalTranscript.toLowerCase(Locale.US)
            .replace("'", "").replaceAll("[^a-z0-9 ]", " ").trim().replaceAll(" +", " ");
        if (text.equals("hey station")) {
            wokeAt = elapsedMillis;
            lastPhrase = "";
            return VoiceCommand.wake();
        }
        String prefix = text.startsWith("hey station ") ? "hey station " : "station ";
        boolean prefixed = text.startsWith(prefix);
        boolean following = listeningForCommand(elapsedMillis);
        if (!prefixed && !following) return VoiceCommand.parse(null);
        String phrase = prefixed ? text.substring(prefix.length()) : text;
        VoiceCommand command = VoiceCommand.parse(phrase);
        if (command.action == VoiceCommand.Action.UNKNOWN) return command;
        if (phrase.equals(lastPhrase) && lastAt >= 0 && elapsedMillis >= lastAt && elapsedMillis - lastAt < 5000)
            return VoiceCommand.parse(null);
        if (prefixed) wokeAt = elapsedMillis;
        lastPhrase = phrase;
        lastAt = elapsedMillis;
        return command;
    }
}
