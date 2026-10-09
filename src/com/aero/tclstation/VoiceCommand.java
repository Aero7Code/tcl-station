package com.aero.tclstation;

import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Explicit, bounded local command grammar; never executes arbitrary recognized text. */
final class VoiceCommand {
    enum Action { WAKE, WEATHER, TIME, CAMERA, CLOSE_CAMERA, SET_TIMER, TIMERS, ALARMS, MUSIC, HOME, HELP, STOP_LISTENING, UNKNOWN }
    private static final Pattern TIMER = Pattern.compile("^(?:set|start) (?:a )?timer for ([a-z0-9]+) (seconds?|minutes?)$");
    // One hundred short utterances, all mapped to existing, bounded actions.
    // Matching is local and O(1); acoustic recognition remains the speech engine's job.
    private static final Map<String, VoiceCommand> CATALOG = new LinkedHashMap<>();
    static {
        add(Action.WEATHER, "current weather|weather right now|what's the weather|how's the weather|tell me the weather|show me the weather|weather outside|what's it like outside|give me the weather|check the weather");
        add(Action.TIME, "what time is it|what's the time|current time|time right now|tell me the time|show me the time|Denver time|time in Denver|give me the time|what time is it in Denver");
        add(Action.CAMERA, "open camera|show camera|start camera|launch camera|camera view|show the camera|open the camera|turn on camera|bring up camera|view camera");
        add(Action.CLOSE_CAMERA, "close camera|hide camera|exit camera|leave camera|dismiss camera|close the camera|hide the camera|exit the camera|leave the camera|turn off camera");
        String[] timerWords = {"one", "two", "three", "five", "ten", "fifteen", "twenty", "twenty five", "thirty", "sixty"};
        int[] timerMinutes = {1, 2, 3, 5, 10, 15, 20, 25, 30, 60};
        for (int i = 0; i < timerWords.length; i++) add("set a " + timerWords[i] + " minute timer", new VoiceCommand(Action.SET_TIMER, timerMinutes[i] * 60));
        add(Action.TIMERS, "open timers|show timers|view timers|my timers|timer list|see my timers|go to timers|bring up timers|timers please|open the timer screen");
        add(Action.ALARMS, "open alarms|show alarms|view alarms|my alarms|alarm list|see my alarms|go to alarms|bring up alarms|alarms please|open the alarm screen");
        add(Action.MUSIC, "open music|show music|launch music|music app|open YouTube Music|launch YouTube Music|show YouTube Music|go to music|bring up music|music please");
        add(Action.HOME, "go home|show home|open home|home screen|Station home|go to Station home|show Station home|return home|back to home|take me home");
        add(Action.STOP_LISTENING, "stop listening|quit listening|end listening|stop voice listening|end voice listening|turn off voice listening|disable voice listening|stop listening now|stop listening please|stop the voice listener");
        if (CATALOG.size() != 100) throw new IllegalStateException("Expected 100 voice phrases");
    }
    private static void add(Action action, String phrases) {
        for (String phrase : phrases.split("\\|")) add(phrase, of(action));
    }
    private static void add(String phrase, VoiceCommand command) {
        if (CATALOG.put(normalize(phrase), command) != null) throw new IllegalStateException("Duplicate voice phrase: " + phrase);
    }
    static Map<String, VoiceCommand> catalog() { return java.util.Collections.unmodifiableMap(CATALOG); }
    private static String normalize(String spoken) {
        return spoken.toLowerCase(Locale.US).replace("'", "").replaceAll("[^a-z0-9 ]", " ").trim().replaceAll(" +", " ");
    }
    final Action action;
    final int seconds;

    private VoiceCommand(Action action, int seconds) {
        this.action = action;
        this.seconds = seconds;
    }

    static VoiceCommand parse(String spoken) {
        if (spoken == null) return new VoiceCommand(Action.UNKNOWN, 0);
        String text = normalize(spoken);
        VoiceCommand catalogCommand = CATALOG.get(text);
        if (catalogCommand != null) return catalogCommand;
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
    static VoiceCommand wake() { return of(Action.WAKE); }

    private static long parseCount(String count) {
        String[] words = {"one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "fifteen", "twenty", "thirty", "sixty", "ninety"};
        int[] values = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 15, 20, 30, 60, 90};
        for (int i = 0; i < words.length; i++) if (words[i].equals(count)) return values[i];
        try { return Long.parseLong(count); }
        catch (NumberFormatException e) { return -1; }
    }
}
