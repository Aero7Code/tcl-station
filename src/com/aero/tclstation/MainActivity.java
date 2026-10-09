package com.aero.tclstation;

import android.app.Activity;
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
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
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
    private static final String[] SUBTITLES = {StationConfig.NAME.toUpperCase(java.util.Locale.US), "PHOTO + VIDEO", "DENVER TIME", "LISTEN", "SOUND DESK", "WATCH DESK", "ASSISTANT"};
    private final ExecutorService network = Executors.newSingleThreadExecutor();
    private final PanelState panels = new PanelState(7, 350);
    private HorizontalScrollView horizontal;
    private TextView pageLabel;
    private TextView weatherSummary;
    private Button refresh;
    private TextView recorderStatus;
    private String weatherText = "Weather: loading…";
    private boolean weatherLoading;
    private int pageWidth;
    private int page;
    private boolean landscape;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
        recorderStatus = null;
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
                TextView note = text(content, "This is a launcher, not a recording or automation service. Future tools are shown honestly until connected.", 16, MUTED, false);
                note.setPadding(dp(6), dp(18), dp(6), dp(4));
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
            ? new LinearLayout.LayoutParams(0, dp(landscape ? 348 : 210), expanded ? 1.8f : 1f)
            : new LinearLayout.LayoutParams(-1, -2);
        position.setMargins(dp(4), dp(3), dp(4), dp(landscape ? 3 : 10));
        cards.addView(card, position);
        TextView number = text(card, String.format(java.util.Locale.US, "%02d  /  %s", index + 1, SUBTITLES[index]), 12, ACCENT[index], true);
        number.setLetterSpacing(.08f);
        TextView title = text(card, TITLES[index], expanded ? 27 : 25, Color.WHITE, true);
        title.setPadding(0, dp(10), 0, dp(4));
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
                case 1: summary = "Capture a moment or shoot a clip."; break;
                case 3: summary = "Music is one tap away."; break;
                case 4: summary = "EQ + routing: NOT IMPLEMENTED"; break;
                case 5: summary = "Visible, local video recording • no audio"; break;
                default: summary = "Integration: NOT IMPLEMENTED";
            }
            text(card, summary, 17, MUTED, false);
        }
        TextView hint = text(card, expanded ? "DOUBLE TAP CARD TO SHRINK" : "TAP TO EXPAND  ↗", 12, ACCENT[index], true);
        hint.setPadding(0, dp(15), 0, dp(3));
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
                action(card, "Take photo", () -> open(new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)));
                action(card, "Record video manually", () -> open(new Intent(MediaStore.INTENT_ACTION_VIDEO_CAMERA)));
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
                break;
            case 5:
                text(card, "Local video only • visible notification • 512 MiB cap. No mic or cloud.", 14, MUTED, false);
                recorderStatus = text(card, "Last reported status: unknown", 14, ACCENT[5], false);
                refreshRecordingStatus();
                LinearLayout controls = new LinearLayout(this);
                controls.setOrientation(LinearLayout.HORIZONTAL);
                card.addView(controls, new LinearLayout.LayoutParams(-1, -2));
                Button start = action(controls, "Start local recording", this::startLocalRecording);
                Button stop = action(controls, "Stop recording", this::stopLocalRecording);
                start.setTextSize(12);
                stop.setTextSize(12);
                start.setLayoutParams(new LinearLayout.LayoutParams(0, dp(48), 1));
                stop.setLayoutParams(new LinearLayout.LayoutParams(0, dp(48), 1));
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
        if (pageLabel != null && horizontal != null) pageLabel.setText((currentPage() + 1) + " / 3    •    SWIPE LEFT / RIGHT");
    }
    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + .5f); }

    private void refreshRecordingStatus() {
        if (recorderStatus == null) return;
        android.content.SharedPreferences prefs = getSharedPreferences(RecorderService.PREFS, MODE_PRIVATE);
        recorderStatus.setText("Last reported status: " + prefs.getString(RecorderService.KEY_STATE, "not started")
            + " — " + prefs.getString(RecorderService.KEY_DETAIL, ""));
    }

    private void startLocalRecording() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, 42);
            return;
        }
        try {
            startForegroundService(new Intent(this, RecorderService.class).setAction(RecorderService.ACTION_START));
            new Handler(Looper.getMainLooper()).postDelayed(this::refreshRecordingStatus, 1300);
        } catch (RuntimeException e) {
            android.widget.Toast.makeText(this, "Could not start recording: " + e.getMessage(), android.widget.Toast.LENGTH_LONG).show();
        }
    }

    private void stopLocalRecording() {
        try {
            startService(new Intent(this, RecorderService.class).setAction(RecorderService.ACTION_STOP));
            new Handler(Looper.getMainLooper()).postDelayed(this::refreshRecordingStatus, 1300);
        } catch (RuntimeException e) {
            android.widget.Toast.makeText(this, "Could not stop recording", android.widget.Toast.LENGTH_LONG).show();
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == 42 && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) startLocalRecording();
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
                if (weatherSummary != null) weatherSummary.setText(weatherText);
                if (refresh != null) refresh.setEnabled(true);
            });
        });
    }

    @Override protected void onDestroy() {
        network.shutdownNow();
        super.onDestroy();
    }
}
