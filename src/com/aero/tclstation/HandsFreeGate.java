package com.aero.tclstation;

import java.util.Locale;

/** Only explicit 'Station ...' final results may dispatch; duplicate commands are rate-limited. */
final class HandsFreeGate {
    private String lastPhrase = "";
    private long lastAt = -1;

    VoiceCommand accept(String finalTranscript, long elapsedMillis) {
        if (finalTranscript == null) return VoiceCommand.parse(null);
        String text = finalTranscript.toLowerCase(Locale.US)
            .replaceAll("[^a-z0-9 ]", " ").trim().replaceAll(" +", " ");
        if (!text.startsWith("station ")) return VoiceCommand.parse(null);
        String phrase = text.substring("station ".length());
        VoiceCommand command = VoiceCommand.parse(phrase);
        if (command.action == VoiceCommand.Action.UNKNOWN) return command;
        if (phrase.equals(lastPhrase) && lastAt >= 0 && elapsedMillis >= lastAt && elapsedMillis - lastAt < 5000)
            return VoiceCommand.parse(null);
        lastPhrase = phrase;
        lastAt = elapsedMillis;
        return command;
    }
}
