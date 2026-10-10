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
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.AlarmClock;
import android.provider.MediaStore;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;

import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.RecognitionListener;
import org.vosk.android.SpeechService;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
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
        + "&current=temperature_2m,weather_code,relative_humidity_2m,apparent_temperature,wind_speed_10m,is_day"
        + "&daily=temperature_2m_max,temperature_2m_min,weather_code,precipitation_probability_max"
        + "&forecast_days=7&temperature_unit=fahrenheit&wind_speed_unit=mph&timezone=America%2FDenver";
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
    private LiveCameraDialog cameraPreview;
    private boolean requestedFront;
    private static final int HOME_REQUEST = 92;
    private static final int MICROPHONE_REQUEST = 44;
    private static final int SPEAK_PERMISSION_REQUEST = 45;
    private static final int NOTIFICATION_REQUEST = 46;
    private static final int SPEAK_TIMEOUT_MS = 9000;
    private static WeakReference<MainActivity> foreground = new WeakReference<>(null);
    private boolean resumed;
    private boolean pushToTalkActive;
    private int tapEpoch;
    private Model tapModel;
    private Recognizer tapRecognizer;
    private SpeechService tapMicrophone;
    private Button speakButton;
    private TextToSpeech speech;
    private boolean speechReady;
    private int speechGeneration;
    private String pendingSpeech;
    private String pendingSpeechId;
    private Button handsFree;
    static final String ACTION_OPEN_WEATHER = "com.aero.tclstation.OPEN_WEATHER";

    static MainActivity foregroundActivity() {
        MainActivity activity = foreground.get();
        return activity != null && activity.resumed ? activity : null;
    }
    private String weatherText = "Weather: loading…";
    private boolean weatherLoading;
    private WeatherForecast weatherForecast = WeatherForecast.parse(null);
    private android.app.Dialog weatherDialog;
    private long weatherLoadedAt;
    private final Handler weatherUi = new Handler(Looper.getMainLooper());
    private final Runnable weatherExpiry = () -> {
        if (!resumed) return;
        if (currentWeatherFresh()) { scheduleWeatherExpiry(); return; }
        if (weatherSummary != null) weatherSummary.setText(weatherDisplayText());
        boolean detailOpen = weatherDialog != null && weatherDialog.isShowing();
        if (detailOpen) { weatherDialog.dismiss(); weatherDialog = null; }
        if (!weatherLoading) loadWeather();
        if (detailOpen) showWeatherDetail(); // Neutral loading scene, never yesterday's forecast.
    };
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
        getSharedPreferences("station", MODE_PRIVATE).edit().remove("last_weather").apply();
        if (savedInstanceState != null) {
            panels.restore(savedInstanceState.getInt("expanded", -1));
            page = Math.max(0, Math.min(2, savedInstanceState.getInt("page", 0)));
        }
        landscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        pageWidth = getResources().getDisplayMetrics().widthPixels;
        buildDashboard();
        horizontal.post(() -> horizontal.scrollTo(page * pageWidth, 0));
        loadWeather();
        if (ACTION_OPEN_WEATHER.equals(getIntent().getAction()))
            horizontal.post(this::openWeatherPanel);
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (ACTION_OPEN_WEATHER.equals(intent.getAction())) openWeatherPanel();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        out.putInt("expanded", panels.expanded());
        out.putInt("page", horizontal == null ? page : Math.max(0, Math.min(2, Math.round((float) horizontal.getScrollX() / pageWidth))));
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
        speakButton = new Button(this);
        speakButton.setText("Speak");
        speakButton.setAllCaps(false);
        speakButton.setContentDescription("Speak a Station command locally; tap again to stop");
        speakButton.setTextColor(BACKGROUND);
        speakButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(ACCENT[0]));
        speakButton.setOnClickListener(v -> startVoiceInput());
        footer.addView(speakButton, new LinearLayout.LayoutParams(dp(130), dp(52)));
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
        card.setContentDescription(TITLES[index] + (expanded
            ? index == 0 ? ", expanded. Tap again for detailed weather; hold to shrink" : ", expanded. Double tap to shrink"
            : ", compact. Tap to expand"));
        card.setOnClickListener(v -> {
            if (index == 0 && panels.expanded() == 0) {
                showWeatherDetail();
                return;
            }
            panels.tap(index, SystemClock.uptimeMillis());
            int x = horizontal.getScrollX();
            buildDashboard();
            horizontal.post(() -> horizontal.scrollTo(x, 0));
        });
        if (index == 0) card.setOnLongClickListener(v -> {
            panels.restore(-1);
            int x = horizontal.getScrollX();
            buildDashboard();
            horizontal.post(() -> horizontal.scrollTo(x, 0));
            return true;
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
            weatherSummary = text(card, weatherDisplayText(), expanded ? 20 : 17, Color.WHITE, false);
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
        TextView hint = text(card, expanded
            ? index == 0 ? "TAP AGAIN FOR DETAILS • HOLD TO SHRINK" : "DOUBLE TAP CARD TO SHRINK"
            : "TAP TO EXPAND  ↗", 12, ACCENT[index], true);
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
                LinearLayout weatherActions = new LinearLayout(this);
                weatherActions.setOrientation(LinearLayout.HORIZONTAL);
                card.addView(weatherActions, new LinearLayout.LayoutParams(-1, -2));
                Button detail = action(weatherActions, "Detailed weather", this::showWeatherDetail);
                detail.setLayoutParams(new LinearLayout.LayoutParams(0, dp(48), 1));
                refresh = action(weatherActions, "Refresh weather", this::loadWeather);
                refresh.setLayoutParams(new LinearLayout.LayoutParams(0, dp(48), 1));
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
                text(card, "Opens YouTube Music; media keys go to Android's active player.", 14, MUTED, false);
                action(card, "Open YouTube Music", this::openMusic);
                LinearLayout mediaActions = new LinearLayout(this);
                mediaActions.setOrientation(LinearLayout.HORIZONTAL);
                card.addView(mediaActions, new LinearLayout.LayoutParams(-1, -2));
                Button play = action(mediaActions, "Play", () -> mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY));
                play.setLayoutParams(new LinearLayout.LayoutParams(0, dp(42), 1));
                Button pause = action(mediaActions, "Pause", () -> mediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE));
                pause.setLayoutParams(new LinearLayout.LayoutParams(0, dp(42), 1));
                Button next = action(mediaActions, "Next", () -> mediaKey(KeyEvent.KEYCODE_MEDIA_NEXT));
                next.setLayoutParams(new LinearLayout.LayoutParams(0, dp(42), 1));
                break;
            case 4:
                text(card, "Android handles audio. No EQ or mixer.", 14, MUTED, false);
                LinearLayout audioActions = new LinearLayout(this);
                audioActions.setOrientation(LinearLayout.HORIZONTAL);
                card.addView(audioActions, new LinearLayout.LayoutParams(-1, -2));
                Button beep = action(audioActions, "Test audio (short beep)", this::testAudio);
                beep.setLayoutParams(new LinearLayout.LayoutParams(0, dp(42), 1));
                Button spoken = action(audioActions, "Test spoken reply", () -> say("Station voice test. If you hear me from the Echo Studio, tablet text-to-speech is working."));
                spoken.setLayoutParams(new LinearLayout.LayoutParams(0, dp(42), 1));
                Button voiceChoice = action(card, StationVoiceProfile.model(this) ? "Voice: offline model (tap for system)" : "Voice: system (tap for offline model)", this::switchVoice);
                voiceChoice.getLayoutParams().height = dp(42);
                voiceChoice.setContentDescription(StationVoiceProfile.model(this)
                    ? "Offline model voice selected. Tap to use the system voice"
                    : "System voice selected. Tap to try the offline model voice");
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

    private void openLiveCamera() { openLiveCamera(false); }

    private void openLiveCamera(boolean front) {
        requestedFront = front;
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, 43);
            return;
        }
        if (liveCameraDialog != null && liveCameraDialog.isShowing()) {
            cameraPreview.selectFacing(front);
            return;
        }
        cameraPreview = LiveCameraDialog.show(this, this::startVoiceInput, front);
        liveCameraDialog = cameraPreview.dialog();
    }

    private void startVoiceInput() {
        if (pushToTalkActive) {
            stopLocalInput();
            showVoiceStatus("Speak cancelled; microphone off");
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, SPEAK_PERMISSION_REQUEST);
            return;
        }
        startLocalInput();
    }

    private void startLocalInput() {
        if (ListeningService.isSpeechActive()) {
            showVoiceStatus("Wait for the spoken reply to finish, then tap Speak");
            return;
        }
        if (liveCameraDialog != null) liveCameraDialog.dismiss();
        liveCameraDialog = null;
        ListeningService.pauseForPushToTalk();
        pushToTalkActive = true;
        final int request = ++tapEpoch;
        speakButton.setText("Speak: loading");
        if (tapModel != null) {
            openLocalMicrophone(request);
            return;
        }
        StationModelLoader.load(this, loaded -> {
            if (!pushToTalkActive || request != tapEpoch || isDestroyed()) {
                loaded.close();
                return;
            }
            tapModel = loaded;
            openLocalMicrophone(request);
        }, error -> {
            if (!pushToTalkActive || request != tapEpoch || isDestroyed()) return;
            stopLocalInput();
            showVoiceStatus("Offline speech model unavailable; microphone off");
        });
    }

    private void openLocalMicrophone(int request) {
        try {
            tapRecognizer = new Recognizer(tapModel, 16000.0f);
            tapMicrophone = new SpeechService(tapRecognizer, 16000.0f);
            if (!tapMicrophone.startListening(new RecognitionListener() {
                @Override public void onPartialResult(String json) {
                    if (request == tapEpoch && pushToTalkActive
                        && !parseVoskText(json, "partial").isEmpty()) speakButton.setText("Speak: hearing");
                }
                @Override public void onResult(String json) { acceptLocalResult(request, json, false); }
                @Override public void onFinalResult(String json) { acceptLocalResult(request, json, true); }
                @Override public void onError(Exception error) {
                    if (request != tapEpoch || !pushToTalkActive) return;
                    stopLocalInput();
                    showVoiceStatus("Local microphone failed; tap Speak to retry");
                }
                @Override public void onTimeout() {
                    if (request != tapEpoch || !pushToTalkActive) return;
                    acceptLocalResult(request, tapRecognizer.getFinalResult(), true);
                }
            }, SPEAK_TIMEOUT_MS)) throw new IOException("Could not start local microphone");
            speakButton.setText("Speak: listening");
            showVoiceStatus("Listening locally on the tablet; tap Speak again to cancel");
        } catch (IOException | SecurityException error) {
            stopLocalInput();
            showVoiceStatus("Local microphone unavailable; tap Speak to retry");
        }
    }

    private static String parseVoskText(String json, String field) {
        try { return new org.json.JSONObject(json).optString(field, "").trim(); }
        catch (org.json.JSONException error) { return ""; }
    }

    private void acceptLocalResult(int request, String json, boolean terminal) {
        if (request != tapEpoch || !pushToTalkActive) return;
        String text = parseVoskText(json, "text");
        if (text.isEmpty() && !terminal) return;
        stopLocalInput();
        if (text.isEmpty()) showVoiceStatus("No command heard; microphone off");
        else handleVoice(VoiceCommand.parse(text));
    }

    private void stopLocalInput() {
        if (!pushToTalkActive) return;
        ++tapEpoch; // Ignore already-posted recognition callbacks and model-load completions.
        pushToTalkActive = false;
        if (tapMicrophone != null) {
            tapMicrophone.cancel();
            tapMicrophone.shutdown();
            tapMicrophone = null;
        }
        if (tapRecognizer != null) {
            tapRecognizer.close();
            tapRecognizer = null;
        }
        if (speakButton != null) speakButton.setText("Speak");
        ListeningService.resumeAfterPushToTalk();
    }

    private void toggleListening() {
        if (ListeningService.isEnabled()) {
            HandsFreePreference.setEnabled(this, false);
            stopService(new Intent(this, ListeningService.class));
            refreshListeningButton();
        } else if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, MICROPHONE_REQUEST);
        } else startListening();
    }

    private void startListening() {
        if (Build.VERSION.SDK_INT >= 33
            && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_REQUEST);
            return;
        }
        if (!ListeningService.notificationsAvailable(this)) {
            showVoiceStatus("Enable Station notifications to keep the microphone Stop control visible");
            return;
        }
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
            ? ListeningService.isCapturing()
                ? "Continuous local microphone on. Tap to stop hands-free listening."
                : "Hands-free enabled; microphone paused or loading. Tap to stop."
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
        }
    }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        foreground = new WeakReference<>(this);
        if (HandsFreePreference.isEnabled(this) && !ListeningService.notificationsAvailable(this)) {
            HandsFreePreference.setEnabled(this, false);
            stopService(new Intent(this, ListeningService.class));
            showVoiceStatus("Notifications off; hands-free stopped so Stop remains visible");
        } else if (HandsFreePreference.isEnabled(this) && !ListeningService.isEnabled()
            && checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
            startListening(); // Recover only after Station becomes visible; Android may block background microphone starts.
        if (weatherLoadedAt > 0 && !weatherLoading && !currentWeatherFresh()) loadWeather();
        else scheduleWeatherExpiry();
        if (weatherSummary != null) weatherSummary.setText(weatherDisplayText());
        refreshListeningButton();
        if (!pushToTalkActive) ListeningService.resumeAfterPushToTalk();
    }

    private void openWeatherPanel() {
        if (weatherDialog != null && weatherDialog.isShowing()) weatherDialog.dismiss();
        if (!weatherLoading && !currentWeatherFresh()) loadWeather();
        panels.restore(0);
        page = 0;
        buildDashboard();
        horizontal.post(() -> { horizontal.scrollTo(0, 0); updatePageLabel(); });
    }

    private void showWeatherDetail() {
        if (weatherDialog != null && weatherDialog.isShowing()) return;
        boolean fresh = !weatherLoading && currentWeatherFresh();
        if (!fresh && !weatherLoading) loadWeather();
        weatherDialog = WeatherDetailDialog.show(this, fresh ? weatherForecast : WeatherForecast.parse(null), this::loadWeather);
    }

    private boolean currentWeatherFresh() {
        return WeatherFreshness.canDisplay(weatherForecast, weatherLoadedAt, SystemClock.elapsedRealtime(),
            java.time.LocalDateTime.now(java.time.ZoneId.of("America/Denver")));
    }

    private String weatherDisplayText() {
        return WeatherFreshness.visibleSummary(weatherForecast, weatherLoadedAt, SystemClock.elapsedRealtime(),
            java.time.LocalDateTime.now(java.time.ZoneId.of("America/Denver")), weatherLoading, weatherText);
    }

    private void scheduleWeatherExpiry() {
        weatherUi.removeCallbacks(weatherExpiry);
        if (!resumed || !currentWeatherFresh()) return;
        java.time.LocalDateTime local = java.time.LocalDateTime.now(java.time.ZoneId.of("America/Denver"));
        long untilCache = 15 * 60_000L - (SystemClock.elapsedRealtime() - weatherLoadedAt);
        long untilObservation = java.time.Duration.between(local,
            weatherForecast.current.observedAt.plusHours(1)).toMillis();
        long untilMidnight = java.time.Duration.between(local,
            local.toLocalDate().plusDays(1).atStartOfDay()).toMillis();
        long delay = Math.max(1_000, Math.min(untilCache, Math.min(untilObservation, untilMidnight)) + 1_000);
        weatherUi.postDelayed(weatherExpiry, delay);
    }

    void handleVoice(VoiceCommand command) {
        switch (command.action) {
            case WAKE:
                say("I'm listening.");
                break;
            case WEATHER:
                openWeatherPanel();
                say(weatherDisplayText().replace("•", ", ").replace("\n", ". "));
                break;
            case TIME:
                SimpleDateFormat format = new SimpleDateFormat("h:mm a", Locale.US);
                format.setTimeZone(TimeZone.getTimeZone("America/Denver"));
                say("It is " + format.format(new Date()) + " Denver time.");
                break;
            case CAMERA:
                showVoiceStatus("Opening live back camera");
                openLiveCamera(false);
                break;
            case CAMERA_FRONT:
                showVoiceStatus("Opening live front camera");
                openLiveCamera(true);
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
            case SET_ALARM:
                showVoiceStatus("Review alarm in Android Clock");
                open(new Intent(AlarmClock.ACTION_SET_ALARM)
                    .putExtra(AlarmClock.EXTRA_HOUR, command.hour)
                    .putExtra(AlarmClock.EXTRA_MINUTES, command.minute)
                    .putExtra(AlarmClock.EXTRA_SKIP_UI, false));
                break;
            case ALARMS:
                open(new Intent(AlarmClock.ACTION_SHOW_ALARMS));
                break;
            case MUSIC:
                openMusic();
                break;
            case PLAY_MEDIA:
                mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY);
                break;
            case PAUSE_MEDIA:
                mediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE);
                break;
            case NEXT_MEDIA:
                mediaKey(KeyEvent.KEYCODE_MEDIA_NEXT);
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
                HandsFreePreference.setEnabled(this, false);
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

    private void switchVoice() {
        // Do not release any microphone hold until the previous engine confirms stop.
        if (speech != null && speech.stop() != TextToSpeech.SUCCESS) {
            showVoiceStatus("Could not stop speech; voice unchanged");
            return;
        }
        ++speechGeneration;
        if (speech != null) { speech.shutdown(); speech = null; }
        speechReady = false;
        pendingSpeech = null;
        pendingSpeechId = null;
        ListeningService.cancelSpokenResponses(this);
        if (!ListeningService.resetVoiceEngine()) {
            showVoiceStatus("Could not stop hands-free speech; voice unchanged");
            return;
        }
        boolean selected = StationVoiceProfile.toggle(this);
        int x = horizontal.getScrollX();
        say(selected ? "Testing the offline model voice." : "Using the system voice.");
        buildDashboard();
        horizontal.post(() -> horizontal.scrollTo(x, 0));
    }

    private void say(String message) {
        showVoiceStatus(message);
        String id = ListeningService.beginSpokenResponse(this);
        watchActivitySpeech(id);
        if (speechReady && speech != null) {
            if (!applyVoiceChoice()) {
                ListeningService.spokenResponseFinished(this, id);
                return;
            }
            if (speech.speak(message, TextToSpeech.QUEUE_FLUSH, null, id) == TextToSpeech.ERROR)
                ListeningService.spokenResponseFinished(this, id);
            return;
        }
        if (pendingSpeechId != null) ListeningService.spokenResponseFinished(this, pendingSpeechId);
        pendingSpeech = message;
        pendingSpeechId = id;
        if (speech == null) {
            final int request = ++speechGeneration;
            speech = StationVoiceProfile.create(this, status -> {
                if (request != speechGeneration) return;
                if (status != TextToSpeech.SUCCESS || speech == null) {
                    showVoiceStatus("Voice engine unavailable; tap Test spoken reply to retry");
                    ListeningService.spokenResponseFinished(this, pendingSpeechId);
                    pendingSpeech = null;
                    pendingSpeechId = null;
                    ++speechGeneration;
                    if (speech != null) { speech.shutdown(); speech = null; }
                    speechReady = false;
                    return;
                }
                speechReady = applyVoiceChoice();
                if (!speechReady) {
                    ListeningService.spokenResponseFinished(this, pendingSpeechId);
                    pendingSpeech = null;
                    pendingSpeechId = null;
                    ++speechGeneration;
                    speech.shutdown();
                    speech = null;
                    return;
                }
                speech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override public void onStart(String id) { }
                    @Override public void onDone(String id) { runOnUiThread(() -> ListeningService.spokenResponseFinished(MainActivity.this, id)); }
                    @Override public void onError(String id) { runOnUiThread(() -> ListeningService.spokenResponseFinished(MainActivity.this, id)); }
                    @Override public void onStop(String id, boolean interrupted) { runOnUiThread(() -> ListeningService.spokenResponseFinished(MainActivity.this, id)); }
                });
                if (pendingSpeech != null) {
                    if (speech.speak(pendingSpeech, TextToSpeech.QUEUE_FLUSH, null, pendingSpeechId) == TextToSpeech.ERROR)
                        ListeningService.spokenResponseFinished(this, pendingSpeechId);
                    pendingSpeech = null;
                    pendingSpeechId = null;
                }
            });
        }
    }

    private void watchActivitySpeech(String id) {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (!ListeningService.isSpokenResponsePending(id)) return;
            // A missing TTS callback cannot hold Speak hostage forever. Never
            // release the hold unless this Activity's engine confirms stop.
            if (speech != null && speech.stop() != TextToSpeech.SUCCESS) {
                android.os.Process.killProcess(android.os.Process.myPid());
                return;
            }
            ++speechGeneration;
            if (speech != null) { speech.shutdown(); speech = null; }
            speechReady = false;
            pendingSpeech = null;
            pendingSpeechId = null;
            ListeningService.cancelSpokenResponses(this);
            showVoiceStatus("Speech timed out; voice stopped. Tap spoken reply to retry");
        }, 15000);
    }

    private boolean applyVoiceChoice() {
        if (StationVoiceProfile.apply(this, speech)) return true;
        showVoiceStatus(StationVoiceProfile.model(this)
            ? "Offline model voice unavailable; no system-voice fallback"
            : "System voice unavailable");
        return false;
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == SPEAK_PERMISSION_REQUEST) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) startLocalInput();
            else showVoiceStatus("Microphone permission denied; Speak remains off");
            return;
        }
        if (requestCode == MICROPHONE_REQUEST) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) startListening();
            else showVoiceStatus("Microphone permission denied; hands-free remains off");
            return;
        }
        if (requestCode == NOTIFICATION_REQUEST) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) startListening();
            else showVoiceStatus("Notification permission denied; hands-free remains off. Enable Station notifications in Android settings.");
            return;
        }
        if (requestCode == 43 && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) openLiveCamera(requestedFront);
    }

    @Override protected void onPause() {
        weatherUi.removeCallbacks(weatherExpiry);
        if (weatherDialog != null && weatherDialog.isShowing()) { weatherDialog.dismiss(); weatherDialog = null; }
        stopLocalInput();
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

    private void openMusic() {
        open(new Intent(Intent.ACTION_VIEW, Uri.parse("https://music.youtube.com/")));
    }

    private void mediaKey(int code) {
        AudioManager audio = getSystemService(AudioManager.class);
        if (audio == null) {
            showVoiceStatus("Android media controls unavailable");
            return;
        }
        // isMusicActive() is false while paused; still send Play so the
        // previous media session has a chance to resume.
        if (code != KeyEvent.KEYCODE_MEDIA_PLAY && !audio.isMusicActive()) {
            showVoiceStatus("No active music playback; open music and select a track first");
            return;
        }
        long at = SystemClock.uptimeMillis();
        audio.dispatchMediaKeyEvent(new KeyEvent(at, at, KeyEvent.ACTION_DOWN, code, 0));
        audio.dispatchMediaKeyEvent(new KeyEvent(at, at, KeyEvent.ACTION_UP, code, 0));
        showVoiceStatus(code == KeyEvent.KEYCODE_MEDIA_PLAY
            ? "Play requested; if nothing starts, open music and choose a track"
            : "Media key sent to Android's active player");
    }

    private void open(Intent intent) {
        try { startActivity(intent); }
        catch (ActivityNotFoundException | SecurityException e) {
            android.widget.Toast.makeText(this, "No compatible app available", android.widget.Toast.LENGTH_LONG).show();
        }
    }

    private void loadWeather() {
        if (weatherLoading) return;
        weatherText = "Weather: loading…";
        weatherLoading = true;
        weatherUi.removeCallbacks(weatherExpiry);
        if (weatherDialog != null && weatherDialog.isShowing()) {
            weatherDialog.dismiss();
            weatherDialog = WeatherDetailDialog.show(this, WeatherForecast.parse(null), this::loadWeather);
        }
        if (weatherSummary != null) weatherSummary.setText(weatherDisplayText());
        if (refresh != null) refresh.setEnabled(false);
        network.execute(() -> {
            WeatherForecast result = null;
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
                            result = WeatherForecast.parse(bytes.toString(StandardCharsets.UTF_8.name()));
                        }
                    }
                } finally { connection.disconnect(); }
            } catch (Exception ignored) { /* Show honest failure state below. */ }
            WeatherForecast value = result;
            runOnUiThread(() -> {
                if (isDestroyed()) return;
                weatherForecast = value != null ? value : WeatherForecast.parse(null);
                WeatherModel current = weatherForecast.current;
                weatherLoadedAt = current.available ? SystemClock.elapsedRealtime() : 0;
                weatherText = current.available
                    ? StationConfig.NAME + ": " + current.temperatureF + "°F  •  " + current.description + "\nAs of " + current.observedTime + " local time"
                    : "Weather unavailable — check connection and retry";
                weatherLoading = false;
                if (weatherSummary != null) weatherSummary.setText(weatherDisplayText());
                if (refresh != null) refresh.setEnabled(true);
                scheduleWeatherExpiry();
                if (weatherDialog != null && weatherDialog.isShowing()) {
                    weatherDialog.dismiss();
                    weatherDialog = WeatherDetailDialog.show(this,
                        currentWeatherFresh() ? weatherForecast : WeatherForecast.parse(null), this::loadWeather);
                }
            });
        });
    }

    @Override protected void onDestroy() {
        weatherUi.removeCallbacks(weatherExpiry);
        if (weatherDialog != null) weatherDialog.dismiss();
        stopLocalInput();
        if (tapModel != null) { tapModel.close(); tapModel = null; }
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
