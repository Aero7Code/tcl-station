package com.aero.tclstation;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Explicit, bounded local command grammar; never executes arbitrary recognized text. */
final class VoiceCommand {
    enum Action { WEATHER, TIME, CAMERA, CLOSE_CAMERA, SET_TIMER, TIMERS, ALARMS, MUSIC, HOME, HELP, STOP_LISTENING, UNKNOWN }
    private static final Pattern TIMER = Pattern.compile("^(?:set|start) (?:a )?timer for ([a-z0-9]+) (seconds?|minutes?)$");
    final Action action;
    final int seconds;

    private VoiceCommand(Action action, int seconds) {
        this.action = action;
        this.seconds = seconds;
    }

    static VoiceCommand parse(String spoken) {
        if (spoken == null) return new VoiceCommand(Action.UNKNOWN, 0);
        String text = spoken.toLowerCase(Locale.US).replace("'", "").replaceAll("[^a-z0-9 ]", " ").trim().replaceAll(" +", " ");
        if (text.matches("(?:whats|what is) the weather|show me the weather|(?:show|read|tell me) (?:the )?weather(?: report)?|weather")) return of(Action.WEATHER);
        if (text.matches("(?:what|whats|what is) (?:the )?time(?: is it)?|tell me the time|time")) return of(Action.TIME);
        if (text.matches("(?:open|show)(?: the)?(?: live)? camera|camera on")) return of(Action.CAMERA);
        if (text.matches("(?:close|stop)(?: the)? camera|camera off")) return of(Action.CLOSE_CAMERA);
        Matcher timer = TIMER.matcher(text);
        if (timer.matches()) {
            long count = parseCount(timer.group(1));
            boolean minutes = timer.group(2).startsWith("minute");
            if (count < 1 || count > (minutes ? 120 : 7200)) return of(Action.UNKNOWN);
            return new VoiceCommand(Action.SET_TIMER, (int) (count * (minutes ? 60 : 1)));
        }
        if (text.matches("(?:open|show)(?: the)? timers?")) return of(Action.TIMERS);
        if (text.matches("(?:open|show)(?: the)? alarms?")) return of(Action.ALARMS);
        if (text.matches("(?:play|open)(?: the)?(?: youtube)? music")) return of(Action.MUSIC);
        if (text.matches("(?:go|take me)(?: to)? home|show station|home")) return of(Action.HOME);
        if (text.matches("help|what can you do")) return of(Action.HELP);
        if (text.matches("stop listening|turn off listening")) return of(Action.STOP_LISTENING);
        return of(Action.UNKNOWN);
    }

    private static VoiceCommand of(Action action) { return new VoiceCommand(action, 0); }

    private static long parseCount(String count) {
        String[] words = {"one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "fifteen", "twenty", "thirty", "sixty", "ninety"};
        int[] values = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 15, 20, 30, 60, 90};
        for (int i = 0; i < words.length; i++) if (words[i].equals(count)) return values[i];
        try { return Long.parseLong(count); }
        catch (NumberFormatException e) { return -1; }
    }
}
