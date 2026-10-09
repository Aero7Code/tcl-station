package com.aero.tclstation;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.RecognitionListener;
import org.vosk.android.SpeechService;
import org.vosk.android.StorageService;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Visible, explicitly started foreground microphone service; never records audio to disk. */
public final class ListeningService extends Service implements RecognitionListener {
    static final String ACTION_START = "com.aero.tclstation.LISTEN_START";
    static final String ACTION_STOP = "com.aero.tclstation.LISTEN_STOP";
    private static final String CHANNEL = "station-microphone";
    private static final int NOTIFICATION_ID = 501;
    private static ListeningService active;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final HandsFreeGate gate = new HandsFreeGate();
    private Model model;
    private Recognizer recognizer;
    private SpeechService microphone;
    private TextToSpeech voice;
    private String pendingSpeech;
    private String pendingSpeechId;
    private boolean voiceReady;
    private boolean loading;
    private boolean capturing;
    // Process-local Activity speech exists before the foreground service is started.
    private static final TtsMicHold speechHold = new TtsMicHold();
    private int generation;
    private int resumeEpoch;

    static boolean isEnabled() { return active != null; }
    static boolean isCapturing() { return active != null && active.capturing; }

    static void pauseForPushToTalk() {
        if (active != null) active.pauseForSpeechUi();
    }
    static void resumeAfterPushToTalk() {
        if (active != null) active.resumeFromSpeechUi();
    }
    static String beginSpokenResponse(Object owner) {
        if (active != null) return active.deferResponse(owner, "station-response");
        return speechHold.begin(owner, "station-response");
    }
    static void spokenResponseFinished(Object owner, String id) {
        if (active != null) active.finishResponse(owner, id);
        else speechHold.finish(owner, id);
    }
    static void cancelSpokenResponses(Object owner) {
        if (speechHold.cancelOwner(owner) && active != null) active.scheduleResume(700);
    }


    @Override public void onCreate() {
        super.onCreate();
        active = this;
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL, "Station hands-free microphone", NotificationManager.IMPORTANCE_LOW));
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            stopSelf();
            return START_NOT_STICKY;
        }
        try { startForeground(NOTIFICATION_ID, notification("Preparing offline speech model")); }
        catch (RuntimeException e) { stopSelf(); return START_NOT_STICKY; }
        // TTS may have begun while hands-free was off. Arm the same fail-closed
        // watchdog used for speech that begins after service startup.
        for (String id : speechHold.heldIds()) watchSpeech(id);
        if (!loading && model == null) {
            loading = true;
            final int request = ++generation;
            StorageService.unpack(this, "model-en-us", "model", loaded -> {
                if (active != this || request != generation) { loaded.close(); return; }
                model = loaded;
                try {
                    recognizer = new Recognizer(model, 16000.0f);
                    resumeMicrophone();
                } catch (IOException | RuntimeException e) { fail("Offline listener unavailable"); }
            }, error -> {
                if (active == this && request == generation) fail("Offline model unavailable");
            });
        }
        refreshButton();
        return START_NOT_STICKY;
    }

    private Notification notification(String status) {
        Intent launch = new Intent(this, MainActivity.class);
        PendingIntent open = PendingIntent.getActivity(this, 0, launch, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent stop = new Intent(this, ListeningService.class).setAction(ACTION_STOP);
        PendingIntent off = PendingIntent.getService(this, 1, stop, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("TCL Station hands-free")
            .setContentText(status)
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "Stop listening", off)
            .build();
    }

    private void notifyStatus(String status) {
        getSystemService(NotificationManager.class).notify(NOTIFICATION_ID, notification(status));
        refreshButton();
    }

    private void refreshButton() {
        MainActivity activity = MainActivity.foregroundActivity();
        if (activity != null) activity.refreshListeningButton();
    }

    private void resumeMicrophone() {
        if (active != this || recognizer == null || capturing || !speechHold.mayResume()) return;
        try {
            microphone = new SpeechService(recognizer, 16000.0f);
            capturing = microphone.startListening(this);
            if (!capturing) { fail("Microphone could not start"); return; }
            notifyStatus("Listening locally • say Station, then a command");
        } catch (IOException | RuntimeException e) { fail("Microphone unavailable"); }
    }

    private void stopMicrophone() {
        capturing = false;
        SpeechService old = microphone;
        microphone = null;
        if (old != null) { old.cancel(); old.shutdown(); }
        refreshButton();
    }

    private void scheduleResume(long delayMillis) {
        int epoch = ++resumeEpoch;
        main.postDelayed(() -> {
            if (active != this || epoch != resumeEpoch || !speechHold.mayResume()) return;
            resumeMicrophone();
        }, delayMillis);
    }

    private void pauseForSpeechUi() {
        speechHold.pauseForPushToTalk();
        ++resumeEpoch;
        stopMicrophone();
        notifyStatus("Paused for push-to-talk");
    }
    private void resumeFromSpeechUi() {
        speechHold.resumeAfterPushToTalk();
        if (speechHold.mayResume()) scheduleResume(700);
    }
    private String deferResponse(Object owner, String prefix) {
        String id = speechHold.begin(owner, prefix);
        ++resumeEpoch;
        stopMicrophone();
        watchSpeech(id);
        return id;
    }
    private void watchSpeech(String id) {
        main.postDelayed(() -> {
            if (active == this && speechHold.isHeld(id)) {
                MainActivity activity = MainActivity.foregroundActivity();
                if (activity != null) activity.showVoiceStatus("Speech stalled; hands-free stopped");
                stopSelf(); // Never resume capture over speech with an unknown end time.
            }
        }, 15000);
    }
    private void finishResponse(Object owner, String id) {
        if (speechHold.finish(owner, id)) scheduleResume(700);
    }

    @Override public void onResult(String json) {
        if (!capturing || !speechHold.mayResume()) return;
        String text;
        try { text = new JSONObject(json).optString("text", ""); }
        catch (Exception e) { return; }
        VoiceCommand command = gate.accept(text, SystemClock.elapsedRealtime());
        if (command.action == VoiceCommand.Action.UNKNOWN) return;
        if (command.action == VoiceCommand.Action.STOP_LISTENING) {
            stopSelf();
            return;
        }
        stopMicrophone();
        MainActivity activity = MainActivity.foregroundActivity();
        if (activity != null) {
            activity.handleVoice(command);
            if (speechHold.mayResume()) scheduleResume(1100);
        } else {
            if (command.action == VoiceCommand.Action.TIME) {
                SimpleDateFormat format = new SimpleDateFormat("h:mm a", Locale.US);
                format.setTimeZone(TimeZone.getTimeZone("America/Denver"));
                speak("It is " + format.format(new Date()) + " Denver time.");
            } else if (command.action == VoiceCommand.Action.WEATHER) {
                String weather = getSharedPreferences("station", MODE_PRIVATE).getString("last_weather", "Open Station to load weather.");
                speak(weather.replace("•", ", ").replace("\n", ". "));
            } else {
                notifyStatus("Command heard; open Station to use it");
                speak("Open Station to use that command.");
            }
        }
    }

    private void speak(String message) {
        String id = deferResponse(this, "station-hands-free");
        if (voiceReady && voice != null) {
            if (voice.speak(message, TextToSpeech.QUEUE_FLUSH, null, id) == TextToSpeech.ERROR) finishResponse(this, id);
            return;
        }
        if (pendingSpeechId != null) finishResponse(this, pendingSpeechId);
        pendingSpeech = message;
        pendingSpeechId = id;
        if (voice == null) voice = new TextToSpeech(this, status -> {
            if (status != TextToSpeech.SUCCESS || voice == null) { finishResponse(this, pendingSpeechId); return; }
            voiceReady = voice.setLanguage(Locale.US) >= 0;
            if (!voiceReady) { finishResponse(this, pendingSpeechId); return; }
            voice.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String id) { }
                @Override public void onDone(String id) { main.post(() -> finishResponse(ListeningService.this, id)); }
                @Override public void onError(String id) { main.post(() -> finishResponse(ListeningService.this, id)); }
                @Override public void onStop(String id, boolean interrupted) { main.post(() -> finishResponse(ListeningService.this, id)); }
            });
            if (pendingSpeech != null) {
                if (voice.speak(pendingSpeech, TextToSpeech.QUEUE_FLUSH, null, pendingSpeechId) == TextToSpeech.ERROR)
                    finishResponse(this, pendingSpeechId);
                pendingSpeech = null;
                pendingSpeechId = null;
            }
        });
    }

    @Override public void onPartialResult(String result) { /* Never dispatch an unfinished phrase. */ }
    @Override public void onFinalResult(String result) { /* stop() flushes stale audio; ignore it. */ }
    @Override public void onError(Exception error) { if (active == this && capturing) fail("Microphone stopped; tap to restart"); }
    @Override public void onTimeout() { if (active == this) fail("Microphone timed out"); }

    private void fail(String message) {
        MainActivity activity = MainActivity.foregroundActivity();
        if (activity != null) activity.showVoiceStatus(message);
        stopSelf();
    }

    @Override public void onDestroy() {
        ++generation;
        ++resumeEpoch;
        stopMicrophone();
        if (recognizer != null) { recognizer.close(); recognizer = null; }
        if (model != null) { model.close(); model = null; }
        boolean voiceStopped = voice == null || voice.stop() == TextToSpeech.SUCCESS;
        if (voice != null) { voice.shutdown(); voice = null; }
        if (voiceStopped) speechHold.cancelOwner(this);
        if (active == this) active = null;
        refreshButton();
        super.onDestroy();
        if (!voiceStopped) android.os.Process.killProcess(android.os.Process.myPid());
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}

// Android-free microphone/TTS hold state; exercised by the host JVM regression test.
final class TtsMicHold {
    private static long nextId;
    private final java.util.Map<String, Object> owners = new java.util.HashMap<>();
    private boolean pausedForPushToTalk;

    String begin(Object owner, String prefix) {
        String id = prefix + "-" + (++nextId);
        owners.put(id, owner);
        return id;
    }

    java.util.List<String> heldIds() { return new java.util.ArrayList<>(owners.keySet()); }

    boolean finish(Object owner, String id) {
        if (id == null || owners.get(id) != owner) return false;
        owners.remove(id);
        return mayResume();
    }
    boolean cancelOwner(Object owner) {
        owners.values().removeIf(value -> value == owner);
        return mayResume();
    }


    boolean isHeld(String id) { return owners.containsKey(id); }
    boolean isWaiting() { return !owners.isEmpty(); }
    void pauseForPushToTalk() { pausedForPushToTalk = true; }
    void resumeAfterPushToTalk() { pausedForPushToTalk = false; }
    boolean mayResume() { return !pausedForPushToTalk && !isWaiting(); }
}
