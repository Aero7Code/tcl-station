package com.aero.tclstation;

import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Explicit, bounded local command grammar; never executes arbitrary recognized text. */
final class VoiceCommand {
    enum Action { WAKE, WEATHER, TIME, CAMERA, CAMERA_FRONT, CLOSE_CAMERA, SET_TIMER, TIMERS, SET_ALARM, ALARMS, MUSIC, PLAY_MEDIA, PAUSE_MEDIA, NEXT_MEDIA, HOME, HELP, STOP_LISTENING, UNKNOWN }
    private static final Pattern TIMER = Pattern.compile("^(?:set|start) (?:a )?timer for ([a-z0-9]+) (seconds?|minutes?)$");
    private static final Pattern ALARM = Pattern.compile("^(?:set (?:an? )?alarm for|wake me (?:up )?at) ([a-z0-9]+)(?: ([a-z0-9]+))? (am|pm)$");
    // One hundred short utterances across fifteen bounded actions, not 100 integrations.
    // Acoustic recognition remains the speech engine's job.
    private static final Map<String, VoiceCommand> CATALOG = new LinkedHashMap<>();
    static {
        add(Action.WEATHER, "current weather|weather right now|what's the weather|how's the weather|tell me the weather|show me the weather|weather outside|what's it like outside|give me the weather|check the weather|what's today's weather|what is the weather like today|show today's weather|weather today");
        add(Action.TIME, "what time is it|what's the time|current time|time right now|tell me the time|Denver time");
        add(Action.CAMERA_FRONT, "open front camera|show front camera|front camera|open the front camera|show me the front camera|switch to front camera|front camera view");
        add(Action.CAMERA, "open camera|open back camera|show back camera|back camera|turn on camera|switch to back camera|show camera");
        add(Action.CLOSE_CAMERA, "close camera|hide camera|exit camera|close the camera|turn off camera");
        String[] timerWords = {"one", "two", "three", "five", "ten", "fifteen", "twenty", "thirty"};
        int[] timerMinutes = {1, 2, 3, 5, 10, 15, 20, 30};
        for (int i = 0; i < timerWords.length; i++) add("set a " + timerWords[i] + " minute timer", new VoiceCommand(Action.SET_TIMER, timerMinutes[i] * 60));
        add(Action.TIMERS, "open timers|show timers|my timers|timer list");
        String[] alarmHours = {"six am", "seven am", "eight am", "nine am", "six pm", "seven pm", "eight pm", "nine pm"};
        for (String hour : alarmHours) add("set alarm for " + hour, parseAlarmTime(hour));
        add(Action.ALARMS, "open alarms|show alarms|my alarms|alarm list|set alarms|show my alarms");
        add(Action.MUSIC, "open music|show music|launch music|open YouTube Music|launch YouTube Music");
        add(Action.PLAY_MEDIA, "play music|resume music|play song|resume song|start music|continue music");
        add(Action.PAUSE_MEDIA, "pause music|pause song|pause the music|pause playback|stop music|stop the song");
        add(Action.NEXT_MEDIA, "skip song|next song|skip this song|play next song|next track|skip track|skip the song");
        add(Action.HOME, "go home|show home|Station home|return home|back to home|take me home");
        add(Action.STOP_LISTENING, "stop listening|end listening|turn off voice listening|stop listening now|stop the voice listener");
        if (CATALOG.size() != 100) throw new IllegalStateException("Expected 100 voice phrases, found " + CATALOG.size());
    }
    private static void add(Action action, String phrases) {
        for (String phrase : phrases.split("\\|")) add(phrase, of(action));
    }
    private static void add(String phrase, VoiceCommand command) {
        if (command.action == Action.UNKNOWN || CATALOG.put(normalize(phrase), command) != null)
            throw new IllegalStateException("Invalid/duplicate voice phrase: " + phrase);
    }
    static Map<String, VoiceCommand> catalog() { return java.util.Collections.unmodifiableMap(CATALOG); }
    private static String normalize(String spoken) {
        return spoken.toLowerCase(Locale.US).replace("'", "").replaceAll("[^a-z0-9 ]", " ").trim().replaceAll(" +", " ");
    }
    final Action action;
    final int seconds;
    final int hour;
    final int minute;
    private VoiceCommand(Action action, int seconds, int hour, int minute) {
        this.action = action;
        this.seconds = seconds;
        this.hour = hour;
        this.minute = minute;
    }
    private VoiceCommand(Action action, int seconds) { this(action, seconds, -1, -1); }

    static VoiceCommand parse(String spoken) {
        if (spoken == null) return of(Action.UNKNOWN);
        String text = normalize(spoken);
        VoiceCommand catalogCommand = CATALOG.get(text);
        if (catalogCommand != null) return catalogCommand;
        if (text.matches("(?:whats|what is) (?:todays? )?weather(?: like today)?|(?:show|read|tell me) (?:the )?weather(?: report)?|weather")) return of(Action.WEATHER);
        if (text.matches("(?:what|whats|what is) (?:the )?time(?: is it)?|tell me the time|time")) return of(Action.TIME);
        if (text.matches("(?:open|show|switch to)(?: the)? front camera|front camera on")) return of(Action.CAMERA_FRONT);
        if (text.matches("(?:open|show)(?: the)?(?: live| back)? camera|camera on")) return of(Action.CAMERA);
        if (text.matches("(?:close|stop)(?: the)? camera|camera off")) return of(Action.CLOSE_CAMERA);
        Matcher timer = TIMER.matcher(text);
        if (timer.matches()) {
            long count = parseCount(timer.group(1));
            boolean minutes = timer.group(2).startsWith("minute");
            if (count < 1 || count > (minutes ? 120 : 7200)) return of(Action.UNKNOWN);
            return new VoiceCommand(Action.SET_TIMER, (int) (count * (minutes ? 60 : 1)));
        }
        if (text.matches("(?:open|show)(?: the)? timers?")) return of(Action.TIMERS);
        Matcher alarm = ALARM.matcher(text);
        if (alarm.matches()) return alarmTime(alarm.group(1), alarm.group(2), alarm.group(3));
        if (text.matches("(?:open|show)(?: the)? alarms?|set alarms")) return of(Action.ALARMS);
        if (text.matches("(?:open|show|launch)(?: the)?(?: youtube)? music")) return of(Action.MUSIC);
        if (text.matches("(?:play|resume|continue)(?: the)? (?:song|music)")) return of(Action.PLAY_MEDIA);
        if (text.matches("(?:pause|stop)(?: the)? (?:song|music|playback)")) return of(Action.PAUSE_MEDIA);
        if (text.matches("(?:skip|next|play next)(?: the| this)? (?:song|track)")) return of(Action.NEXT_MEDIA);
        if (text.matches("(?:go|take me)(?: to)? home|show station|home")) return of(Action.HOME);
        if (text.matches("help|what can you do")) return of(Action.HELP);
        if (text.matches("stop listening|turn off listening")) return of(Action.STOP_LISTENING);
        return of(Action.UNKNOWN);
    }
    private static VoiceCommand of(Action action) { return new VoiceCommand(action, 0); }
    static VoiceCommand wake() { return of(Action.WAKE); }
    private static VoiceCommand parseAlarmTime(String time) {
        String[] parts = time.split(" ");
        return alarmTime(parts[0], null, parts[1]);
    }
    private static VoiceCommand alarmTime(String hourText, String minuteText, String period) {
        long hour = parseCount(hourText);
        long minute = minuteText == null ? 0 : parseCount(minuteText);
        if (hour < 1 || hour > 12 || minute < 0 || minute > 59) return of(Action.UNKNOWN);
        return new VoiceCommand(Action.SET_ALARM, 0, (int) (hour % 12 + ("pm".equals(period) ? 12 : 0)), (int) minute);
    }
    private static long parseCount(String count) {
        String[] words = {"zero", "oh", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "fifteen", "twenty", "thirty", "sixty", "ninety"};
        int[] values = {0, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 15, 20, 30, 60, 90};
        for (int i = 0; i < words.length; i++) if (words[i].equals(count)) return values[i];
        try { return Long.parseLong(count); }
        catch (NumberFormatException e) { return -1; }
    }
}
