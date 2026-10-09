package com.aero.tclstation;

import android.app.Activity;
import android.app.role.RoleManager;
import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.AlarmClock;
import android.provider.MediaStore;
import android.provider.Settings;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.lang.ref.WeakReference;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Seven native cards across three horizontal pages; Android owns media and clock actions. */
public final class MainActivity extends Activity {
    // Default is a city-center example. A private build-time override is never committed.
    private static final String WEATHER_URL = "https://api.open-meteo.com/v1/forecast?latitude="
        + StationConfig.LATITUDE + "&longitude=" + StationConfig.LONGITUDE
        + "&current=temperature_2m,weather_code&temperature_unit=fahrenheit&timezone=America%2FDenver";
    private static final int BACKGROUND = 0xff101b25;
    private static final int MUTED = 0xffc5d3d9;
    private static final int[] ACCENT = {0xff8ce6d1, 0xffffbd81, 0xffafd0ff, 0xfff3b4ca, 0xffffd779, 0xfff2a5a1, 0xffb9b7ff};
    private static final String[] TITLES = {"Weather", "Camera", "Clock", "Music", "Studio / Audio", "Security", "Hermes"};
    private static final String[] SUBTITLES = {StationConfig.NAME.toUpperCase(java.util.Locale.US), "LIVE + PHOTO", "DENVER TIME", "LISTEN", "SOUND DESK", "WATCH DESK", "ASSISTANT"};
    private final ExecutorService network = Executors.newSingleThreadExecutor();
    private final PanelState panels = new PanelState(7, 350);
    private HorizontalScrollView horizontal;
    private TextView pageLabel;
    private TextView weatherSummary;
    private Button refresh;
    private android.app.AlertDialog liveCameraDialog;
    private static final int VOICE_REQUEST = 91;
    private static final int HOME_REQUEST = 92;
    private static final int MICROPHONE_REQUEST = 44;
    private static WeakReference<MainActivity> foreground = new WeakReference<>(null);
    private VoiceCommand pendingVoice;
    private boolean resumed;
    private boolean pushToTalkActive;
    private TextToSpeech speech;
    private boolean speechReady;
    private String pendingSpeech;
    private String pendingSpeechId;
    private Button handsFree;

    static MainActivity foregroundActivity() {
        MainActivity activity = foreground.get();
        return activity != null && activity.resumed ? activity : null;
    }
    private String weatherText = "Weather: loading…";
    private boolean weatherLoading;
    private int pageWidth;
    private int page;
    private boolean landscape;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            LegacyClipCleanup.remove(new File(getFilesDir(), "security-recordings"));
            deleteSharedPreferences("recorder_status");
        } catch (IOException e) {
            android.util.Log.e("TCLStation", "Could not remove legacy camera clips", e);
            android.widget.Toast.makeText(this, "Old camera clips could not be removed. Clear this app's storage in Android Settings.", android.widget.Toast.LENGTH_LONG).show();
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (savedInstanceState != null) {
            panels.restore(savedInstanceState.getInt("expanded", -1));
            page = Math.max(0, Math.min(2, savedInstanceState.getInt("page", 0)));
            weatherText = savedInstanceState.getString("weather", weatherText);
        }
        landscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        pageWidth = getResources().getDisplayMetrics().widthPixels;
        buildDashboard();
        horizontal.post(() -> horizontal.scrollTo(page * pageWidth, 0));
        loadWeather();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        out.putInt("expanded", panels.expanded());
        out.putInt("page", horizontal == null ? page : Math.max(0, Math.min(2, Math.round((float) horizontal.getScrollX() / pageWidth))));
        out.putString("weather", weatherText);
        super.onSaveInstanceState(out);
    }

    private void buildDashboard() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BACKGROUND);
        setContentView(root);

        LinearLayout masthead = new LinearLayout(this);
        masthead.setOrientation(LinearLayout.HORIZONTAL);
        masthead.setGravity(Gravity.CENTER_VERTICAL);
        masthead.setPadding(dp(20), dp(10), dp(20), dp(8));
        root.addView(masthead, new LinearLayout.LayoutParams(-1, dp(82)));
        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.VERTICAL);
        masthead.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        text(heading, "TCL  /  STATION", 24, Color.WHITE, true);
        text(heading, "Your tabletop control deck", 14, MUTED, false);
        TextClock topClock = new TextClock(this);
        topClock.setFormat12Hour("h:mm a");
        topClock.setFormat24Hour("HH:mm");
        topClock.setTimeZone("America/Denver");
        topClock.setTextSize(23);
        topClock.setTextColor(ACCENT[0]);
        masthead.addView(topClock);

        horizontal = new HorizontalScrollView(this);
        horizontal.setHorizontalScrollBarEnabled(false);
        horizontal.setFillViewport(true);
        horizontal.setOverScrollMode(View.OVER_SCROLL_NEVER);
        root.addView(horizontal, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout strip = new LinearLayout(this);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        horizontal.addView(strip);
        weatherSummary = null;
        refresh = null;

        for (int p = 0; p < 3; p++) {
            ScrollView scroller = new ScrollView(this);
            scroller.setFillViewport(true);
            scroller.setVerticalScrollBarEnabled(false);
            strip.addView(scroller, new LinearLayout.LayoutParams(pageWidth, -1));
            LinearLayout content = new LinearLayout(this);
            content.setPadding(dp(16), dp(6), dp(16), dp(8));
            content.setOrientation(LinearLayout.VERTICAL);
            scroller.addView(content);
            String tagline = p == 0 ? "THE ESSENTIALS" : p == 1 ? "SOUND & SAFETY" : "WHAT'S NEXT";
            TextView section = text(content, tagline + "    /    0" + (p + 1), 13, ACCENT[p], true);
            section.setPadding(dp(3), 0, 0, dp(9));
            LinearLayout cards = new LinearLayout(this);
            cards.setOrientation(landscape ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
            content.addView(cards, new LinearLayout.LayoutParams(-1, -2));
            int start = p == 0 ? 0 : p == 1 ? 3 : 6;
            int end = p == 0 ? 3 : p == 1 ? 6 : 7;
            for (int i = start; i < end; i++) addPanel(cards, i);
            if (p == 2) {
                TextView note = text(content, "Choose Station as Home. Android Home settings can restore your old launcher.", 16, MUTED, false);
                note.setPadding(dp(6), dp(3), dp(6), dp(4));
                action(content, "Make Station Home", this::requestHomeRole);
                action(content, "Home app settings", this::openHomeSettings);
            }
        }
        LinearLayout footer = new LinearLayout(this);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        footer.setPadding(dp(12), dp(4), dp(12), dp(6));
        root.addView(footer, new LinearLayout.LayoutParams(-1, dp(66)));
        navButton(footer, "‹", () -> goToPage(Math.max(0, currentPage() - 1)));
        pageLabel = text(footer, "", 14, MUTED, true);
        pageLabel.setGravity(Gravity.CENTER);
        footer.removeView(pageLabel);
        footer.addView(pageLabel, new LinearLayout.LayoutParams(0, -2, 1));
        handsFree = new Button(this);
        handsFree.setAllCaps(false);
        handsFree.setTextSize(13);
        handsFree.setTextColor(BACKGROUND);
        handsFree.setBackgroundTintList(android.content.res.ColorStateList.valueOf(ACCENT[0]));
        handsFree.setOnClickListener(v -> toggleListening());
        footer.addView(handsFree, new LinearLayout.LayoutParams(dp(166), dp(52)));
        refreshListeningButton();
        Button speak = new Button(this);
        speak.setText("Speak");
        speak.setAllCaps(false);
        speak.setContentDescription("Speak a Station command; microphone is off until tapped");
        speak.setTextColor(BACKGROUND);
        speak.setBackgroundTintList(android.content.res.ColorStateList.valueOf(ACCENT[0]));
        speak.setOnClickListener(v -> startVoiceInput());
        footer.addView(speak, new LinearLayout.LayoutParams(dp(130), dp(52)));
        navButton(footer, "›", () -> goToPage(Math.min(2, currentPage() + 1)));
        horizontal.setOnScrollChangeListener((v, x, y, oldX, oldY) -> updatePageLabel());
        updatePageLabel();
    }

    private void addPanel(LinearLayout cards, int index) {
        boolean expanded = panels.expanded() == index;
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(17), dp(16), dp(17), dp(12));
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(expanded ? 0xff263646 : 0xff1c2b36);
        shape.setCornerRadius(dp(18));
        shape.setStroke(dp(2), expanded ? ACCENT[index] : 0xff36505b);
        card.setBackground(shape);
        card.setElevation(dp(2));
        card.setClickable(true);
        card.setFocusable(true);
        card.setContentDescription(TITLES[index] + (expanded ? ", expanded. Double tap to shrink" : ", compact. Tap to expand"));
        card.setOnClickListener(v -> {
            panels.tap(index, SystemClock.uptimeMillis());
            int x = horizontal.getScrollX();
            buildDashboard();
            horizontal.post(() -> horizontal.scrollTo(x, 0));
        });
        LinearLayout.LayoutParams position = landscape
            ? new LinearLayout.LayoutParams(0, dp(index == 6 ? (expanded ? 194 : 136) : 348), expanded ? 1.8f : 1f)
            : new LinearLayout.LayoutParams(-1, -2);
        position.setMargins(dp(4), dp(3), dp(4), dp(landscape ? 3 : 10));
        cards.addView(card, position);
        TextView number = text(card, String.format(java.util.Locale.US, "%02d  /  %s", index + 1, SUBTITLES[index]), 12, ACCENT[index], true);
        number.setLetterSpacing(.08f);
        TextView title = text(card, TITLES[index], expanded ? 27 : 25, Color.WHITE, true);
        title.setPadding(0, dp(index == 6 ? 4 : 10), 0, dp(4));
        if (index == 0) {
            weatherSummary = text(card, weatherText, expanded ? 20 : 17, Color.WHITE, false);
            weatherSummary.setMaxLines(expanded ? 3 : 4);
        } else if (index == 2) {
            TextClock clock = new TextClock(this);
            clock.setFormat12Hour("h:mm a");
            clock.setFormat24Hour("HH:mm");
            clock.setTimeZone("America/Denver");
            clock.setTextSize(expanded ? 36 : 28);
            clock.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            clock.setTextColor(ACCENT[2]);
            card.addView(clock);
        } else {
            String summary;
            switch (index) {
                case 1: summary = "Live on-tablet camera • no recording."; break;
                case 3: summary = "Music is one tap away."; break;
                case 4: summary = "EQ + routing: NOT IMPLEMENTED"; break;
                case 5: summary = "Live viewing only • nothing saved."; break;
                default: summary = "Integration: NOT IMPLEMENTED";
            }
            text(card, summary, index == 6 ? 15 : 17, MUTED, false);
        }
        TextView hint = text(card, expanded ? "DOUBLE TAP CARD TO SHRINK" : "TAP TO EXPAND  ↗", 12, ACCENT[index], true);
        hint.setPadding(0, dp(index == 6 ? 4 : 15), 0, dp(3));
        if (expanded) addDetails(card, index);
        if (!landscape) card.setMinimumHeight(dp(expanded ? 300 : 166));
    }

    private void addDetails(LinearLayout card, int index) {
        View line = new View(this);
        line.setBackgroundColor(0xff506470);
        LinearLayout.LayoutParams divider = new LinearLayout.LayoutParams(-1, dp(1));
        divider.setMargins(0, dp(6), 0, dp(7));
        card.addView(line, divider);
        switch (index) {
            case 0:
                text(card, "Configured area point • Open-Meteo. Not device GPS.", 14, MUTED, false);
                refresh = action(card, "Refresh weather", this::loadWeather);
                refresh.setEnabled(!weatherLoading);
                break;
            case 1:
                text(card, "Preview closes when you leave the app. No video is saved or transmitted.", 14, MUTED, false);
                action(card, "Open live camera", this::openLiveCamera);
                action(card, "Take photo", () -> open(new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)));
                break;
            case 2:
                action(card, "Open timer", () -> open(new Intent(AlarmClock.ACTION_SHOW_TIMERS)));
                action(card, "Open alarms", () -> open(new Intent(AlarmClock.ACTION_SHOW_ALARMS)));
                break;
            case 3:
                text(card, "Opens YouTube Music in an available app or browser.", 14, MUTED, false);
                action(card, "Open YouTube Music", () -> open(new Intent(Intent.ACTION_VIEW, Uri.parse("https://music.youtube.com/"))));
                break;
            case 4:
                text(card, "Audio output is managed by Android. No connected-device monitor, mixer or EQ yet.", 15, MUTED, false);
                action(card, "Test audio (short beep)", this::testAudio);
                action(card, "Test spoken reply", () -> say("Station voice test. If you hear me from the Echo Studio, tablet text-to-speech is working."));
                break;
            case 5:
                text(card, "Open the live preview while present. No background camera, video clips, cloud stream, or alerts.", 14, MUTED, false);
                action(card, "Open live camera", this::openLiveCamera);
                break;
            default:
                text(card, "No Hermes connection, controls or messages are available yet.", 15, MUTED, false);
        }
    }

    private TextView text(LinearLayout parent, String value, int sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        parent.addView(view, new LinearLayout.LayoutParams(-1, -2));
        return view;
    }

    private Button action(LinearLayout card, String title, Runnable work) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(title);
        button.setTextSize(15);
        button.setTextColor(BACKGROUND);
        button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(ACCENT[findPanel(card)]));
        button.setOnClickListener(v -> work.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(48));
        params.topMargin = dp(5);
        card.addView(button, params);
        return button;
    }

    private int findPanel(LinearLayout card) {
        View view = card;
        while (view != null) {
            CharSequence description = view.getContentDescription();
            if (description != null) {
                for (int i = 0; i < TITLES.length; i++) if (description.toString().startsWith(TITLES[i] + ",")) return i;
            }
            view = view.getParent() instanceof View ? (View) view.getParent() : null;
        }
        return 0;
    }

    private void navButton(LinearLayout footer, String symbol, Runnable work) {
        Button button = new Button(this);
        button.setText(symbol);
        button.setTextSize(24);
        button.setTextColor(Color.WHITE);
        button.setContentDescription(symbol.equals("‹") ? "Previous page" : "Next page");
        button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff304652));
        button.setOnClickListener(v -> work.run());
        footer.addView(button, new LinearLayout.LayoutParams(dp(70), dp(52)));
    }

    private int currentPage() { return Math.max(0, Math.min(2, Math.round((float) horizontal.getScrollX() / pageWidth))); }
    private void goToPage(int target) { page = target; horizontal.smoothScrollTo(page * pageWidth, 0); updatePageLabel(); }
    private void updatePageLabel() {
        if (pageLabel == null || horizontal == null) return;
        String count = (currentPage() + 1) + " / 3";
        boolean portrait = getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT;
        pageLabel.setText(portrait ? count : count + "    •    SWIPE LEFT / RIGHT");
        pageLabel.setContentDescription(count + "; swipe left or right to change page");
    }
    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + .5f); }

    private void openLiveCamera() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, 43);
            return;
        }
        if (liveCameraDialog != null && liveCameraDialog.isShowing()) return;
        liveCameraDialog = LiveCameraDialog.show(this, this::startVoiceInput);
    }

    private void startVoiceInput() {
        pushToTalkActive = ListeningService.isEnabled();
        ListeningService.pauseForPushToTalk();
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Try: show weather, open camera, set timer for five minutes");
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        try { startActivityForResult(intent, VOICE_REQUEST); }
        catch (ActivityNotFoundException | SecurityException e) {
            pushToTalkActive = false;
            ListeningService.resumeAfterPushToTalk();
            showVoiceStatus("Speech recognition app unavailable");
        }
    }

    private void toggleListening() {
        if (ListeningService.isEnabled()) {
            stopService(new Intent(this, ListeningService.class));
            refreshListeningButton();
        } else if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, MICROPHONE_REQUEST);
        } else startListening();
    }

    private void startListening() {
        try {
            startForegroundService(new Intent(this, ListeningService.class).setAction(ListeningService.ACTION_START));
            handsFree.setText("Hands-free: Loading");
        } catch (RuntimeException e) { showVoiceStatus("Hands-free could not start; use Speak instead"); }
    }

    void refreshListeningButton() {
        if (handsFree == null) return;
        boolean on = ListeningService.isEnabled();
        handsFree.setText(on ? ListeningService.isCapturing() ? "Hands-free: On" : "Hands-free: Loading" : "Hands-free: Off");
        handsFree.setContentDescription(on
            ? "Continuous local microphone on. Tap to stop hands-free listening."
            : "Microphone off. Tap to enable local hands-free listening, including while the screen is off.");
    }

    private void requestHomeRole() {
        RoleManager roles = getSystemService(RoleManager.class);
        if (roles == null || !roles.isRoleAvailable(RoleManager.ROLE_HOME)) {
            openHomeSettings();
        } else if (roles.isRoleHeld(RoleManager.ROLE_HOME)) {
            showVoiceStatus("Station is already your Home app");
        } else {
            try { startActivityForResult(roles.createRequestRoleIntent(RoleManager.ROLE_HOME), HOME_REQUEST); }
            catch (ActivityNotFoundException | SecurityException e) { openHomeSettings(); }
        }
    }

    private void openHomeSettings() {
        try { startActivity(new Intent(Settings.ACTION_HOME_SETTINGS)); }
        catch (ActivityNotFoundException | SecurityException e) { open(new Intent(Settings.ACTION_SETTINGS)); }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == HOME_REQUEST) {
            RoleManager roles = getSystemService(RoleManager.class);
            showVoiceStatus(roles != null && roles.isRoleHeld(RoleManager.ROLE_HOME) ? "Station is now Home" : "Home app unchanged");
            return;
        }
        if (requestCode != VOICE_REQUEST) return;
        pushToTalkActive = false;
        if (resultCode == RESULT_OK && data != null) {
            ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (results != null && !results.isEmpty()) {
                VoiceCommand command = VoiceCommand.parse(results.get(0));
                if (resumed) handleVoice(command);
                else pendingVoice = command;
            }
        }
        // The recognizer can return to the previous launcher instead of resuming Station.
        // Its result is terminal; release the push-to-talk hold even if this Activity stays paused.
        ListeningService.resumeAfterPushToTalk();
    }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        foreground = new WeakReference<>(this);
        refreshListeningButton();
        if (pendingVoice != null) {
            VoiceCommand command = pendingVoice;
            pendingVoice = null;
            new Handler(Looper.getMainLooper()).post(() -> {
                if (resumed) { handleVoice(command); ListeningService.resumeAfterPushToTalk(); }
            });
        } else if (!pushToTalkActive) ListeningService.resumeAfterPushToTalk();
    }

    void handleVoice(VoiceCommand command) {
        switch (command.action) {
            case WAKE:
                say("I'm listening.");
                break;
            case WEATHER:
                say(weatherText.replace("•", ", ").replace("\n", ". "));
                break;
            case TIME:
                SimpleDateFormat format = new SimpleDateFormat("h:mm a", Locale.US);
                format.setTimeZone(TimeZone.getTimeZone("America/Denver"));
                say("It is " + format.format(new Date()) + " Denver time.");
                break;
            case CAMERA:
                showVoiceStatus("Opening live camera");
                openLiveCamera();
                break;
            case CLOSE_CAMERA:
                if (liveCameraDialog != null) liveCameraDialog.dismiss();
                liveCameraDialog = null;
                say("Camera closed");
                break;
            case SET_TIMER:
                showVoiceStatus("Opening timer for " + command.seconds + " seconds");
                open(new Intent(AlarmClock.ACTION_SET_TIMER)
                    .putExtra(AlarmClock.EXTRA_LENGTH, command.seconds)
                    .putExtra(AlarmClock.EXTRA_SKIP_UI, false));
                break;
            case TIMERS:
                open(new Intent(AlarmClock.ACTION_SHOW_TIMERS));
                break;
            case ALARMS:
                open(new Intent(AlarmClock.ACTION_SHOW_ALARMS));
                break;
            case MUSIC:
                open(new Intent(Intent.ACTION_VIEW, Uri.parse("https://music.youtube.com/")));
                break;
            case HOME:
                if (liveCameraDialog != null) liveCameraDialog.dismiss();
                liveCameraDialog = null;
                goToPage(0);
                say("Station home");
                break;
            case HELP:
                say("For hands-free, say Hey Station, then a command. Try: Hey Station show weather, or Hey Station stop listening. The Speak button still works without a wake phrase.");
                break;
            case STOP_LISTENING:
                stopService(new Intent(this, ListeningService.class));
                say("Hands-free listening stopped");
                break;
            default:
                say("Command not recognized. Say help for the available commands.");
        }
    }

    void showVoiceStatus(String message) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show();
    }

    private void say(String message) {
        showVoiceStatus(message);
        String id = ListeningService.beginSpokenResponse(this);
        if (speechReady && speech != null) {
            if (speech.speak(message, TextToSpeech.QUEUE_FLUSH, null, id) == TextToSpeech.ERROR)
                ListeningService.spokenResponseFinished(this, id);
            return;
        }
        if (pendingSpeechId != null) ListeningService.spokenResponseFinished(this, pendingSpeechId);
        pendingSpeech = message;
        pendingSpeechId = id;
        if (speech == null) speech = new TextToSpeech(this, status -> {
            if (status != TextToSpeech.SUCCESS || speech == null) {
                ListeningService.spokenResponseFinished(this, pendingSpeechId);
                return;
            }
            speechReady = speech.setLanguage(Locale.US) >= 0;
            if (!speechReady) { ListeningService.spokenResponseFinished(this, pendingSpeechId); return; }
            speech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String id) { }
                @Override public void onDone(String id) { runOnUiThread(() -> ListeningService.spokenResponseFinished(MainActivity.this, id)); }
                @Override public void onError(String id) { runOnUiThread(() -> ListeningService.spokenResponseFinished(MainActivity.this, id)); }
                @Override public void onStop(String id, boolean interrupted) { runOnUiThread(() -> ListeningService.spokenResponseFinished(MainActivity.this, id)); }
            });
            if (speechReady && pendingSpeech != null) {
                if (speech.speak(pendingSpeech, TextToSpeech.QUEUE_FLUSH, null, pendingSpeechId) == TextToSpeech.ERROR)
                    ListeningService.spokenResponseFinished(this, pendingSpeechId);
                pendingSpeech = null;
                pendingSpeechId = null;
            }
        });
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == MICROPHONE_REQUEST) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) startListening();
            else showVoiceStatus("Microphone permission denied; Speak still works");
            return;
        }
        if (requestCode == 43 && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) openLiveCamera();
    }

    @Override protected void onPause() {
        resumed = false;
        foreground = new WeakReference<>(null);
        if (liveCameraDialog != null) liveCameraDialog.dismiss();
        liveCameraDialog = null;
        super.onPause();
    }

    private void testAudio() {
        try {
            ToneGenerator tone = new ToneGenerator(AudioManager.STREAM_MUSIC, 25);
            tone.startTone(ToneGenerator.TONE_PROP_BEEP, 500);
            new Handler(Looper.getMainLooper()).postDelayed(tone::release, 800);
        } catch (RuntimeException e) {
            android.widget.Toast.makeText(this, "Audio test unavailable", android.widget.Toast.LENGTH_LONG).show();
        }
    }

    private void open(Intent intent) {
        try { startActivity(intent); }
        catch (ActivityNotFoundException | SecurityException e) {
            android.widget.Toast.makeText(this, "No compatible app available", android.widget.Toast.LENGTH_LONG).show();
        }
    }

    private void loadWeather() {
        weatherText = "Weather: loading…";
        weatherLoading = true;
        if (weatherSummary != null) weatherSummary.setText(weatherText);
        if (refresh != null) refresh.setEnabled(false);
        network.execute(() -> {
            WeatherModel result = null;
            try {
                HttpURLConnection connection = (HttpURLConnection) URI.create(WEATHER_URL).toURL().openConnection();
                connection.setConnectTimeout(7000);
                connection.setReadTimeout(7000);
                try {
                    if (connection.getResponseCode() == 200) {
                        try (InputStream stream = connection.getInputStream(); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                            byte[] buffer = new byte[4096];
                            int count;
                            while ((count = stream.read(buffer)) != -1 && bytes.size() <= 100_000) bytes.write(buffer, 0, count);
                            result = WeatherModel.parse(bytes.toString(StandardCharsets.UTF_8.name()));
                        }
                    }
                } finally { connection.disconnect(); }
            } catch (Exception ignored) { /* Show honest failure state below. */ }
            WeatherModel value = result;
            runOnUiThread(() -> {
                if (isDestroyed()) return;
                weatherText = value != null && value.available
                    ? StationConfig.NAME + ": " + value.temperatureF + "°F  •  " + value.description + "\nAs of " + value.observedTime + " local time"
                    : "Weather unavailable — check connection and retry";
                weatherLoading = false;
                getSharedPreferences("station", MODE_PRIVATE).edit().putString("last_weather", weatherText).apply();
                if (weatherSummary != null) weatherSummary.setText(weatherText);
                if (refresh != null) refresh.setEnabled(true);
            });
        });
    }

    @Override protected void onDestroy() {
        boolean speechStopped = speech == null || speech.stop() == TextToSpeech.SUCCESS;
        if (speech != null) speech.shutdown();
        if (speechStopped) ListeningService.cancelSpokenResponses(this);
        // Binder death stops orphaned TTS when the engine could not confirm stop.
        // Never resume a microphone over uncertain playback or retain the hold forever.
        network.shutdownNow();
        super.onDestroy();
        if (!speechStopped) android.os.Process.killProcess(android.os.Process.myPid());
    }
}
