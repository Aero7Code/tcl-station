package com.aero.tclstation;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.SessionConfiguration;
import android.graphics.drawable.Icon;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.StatFs;
import android.util.Size;
import android.view.Surface;

import java.io.File;
import java.io.IOException;
import java.util.Collections;

/** Explicit, video-only, app-private local recorder. No network or microphone APIs. */
public final class RecorderService extends Service {
    public static final String ACTION_START = "com.aero.tclstation.record.START";
    public static final String ACTION_STOP = "com.aero.tclstation.record.STOP";
    public static final String PREFS = "recorder_status";
    public static final String KEY_STATE = "state";
    public static final String KEY_DETAIL = "detail";
    private static final String CHANNEL = "station_recording";
    private static final int NOTIFICATION_ID = 4102;
    private static final long SEGMENT_MS = 295_000L;
    private static final long MAX_SEGMENT_BYTES = 96L * 1024 * 1024;
    private static final long TOTAL_BUDGET_BYTES = 512L * 1024 * 1024;
    private static final long MIN_FREE_BYTES = 200L * 1024 * 1024;
    // Reserve room for one open .partial file; completed clips plus open clip stay <= 512 MiB.
    private final RecordingPolicy policy = new RecordingPolicy(20, TOTAL_BUDGET_BYTES - MAX_SEGMENT_BYTES);
    private HandlerThread thread;
    private Handler handler;
    private CameraDevice camera;
    private CameraCaptureSession session;
    private MediaRecorder recorder;
    private File partial;
    private boolean recording;
    private boolean requested;
    private int generation;
    private int fileSequence;
    private Size size;
    private String cameraId;
    private Runnable rotation;

    @Override public void onCreate() {
        super.onCreate();
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL, "Security recording", NotificationManager.IMPORTANCE_LOW));
        thread = new HandlerThread("station-camera");
        thread.start();
        handler = new Handler(thread.getLooper());
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || !ACTION_START.equals(intent.getAction()) && !ACTION_STOP.equals(intent.getAction())) {
            stopSelfResult(startId);
            return START_NOT_STICKY;
        }
        if (ACTION_STOP.equals(intent.getAction())) {
            handler.post(() -> {
                requested = false;
                if (release(true)) status("stopped", "Stopped by user");
                stopForeground(STOP_FOREGROUND_REMOVE);
                stopSelf();
            });
            return START_NOT_STICKY;
        }
        try {
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
                throw new SecurityException("Camera permission not granted");
            startForeground(NOTIFICATION_ID, notification("Starting local video recording"), ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA);
            handler.post(() -> {
                if (requested) return;
                requested = true;
                status("starting", "Opening camera");
                try {
                    File dir = directory();
                    cleanupOrphans(dir);
                    policy.prune(dir, null);
                    selectCamera();
                    CameraManager manager = getSystemService(CameraManager.class);
                    manager.openCamera(cameraId, cameraCallback, handler);
                } catch (Exception e) { fail("Camera startup failed: " + e.getMessage()); }
            });
        } catch (RuntimeException e) {
            status("error", "Could not start camera service: " + e.getMessage());
            stopSelfResult(startId);
        }
        return START_NOT_STICKY; // Never silently restart capture after process death.
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    // Camera2 output sizes must come from the camera's recorder-surface configuration.
    private void selectCamera() throws CameraAccessException {
        CameraManager manager = getSystemService(CameraManager.class);
        String selected = null;
        Size chosen = null;
        for (String id : manager.getCameraIdList()) {
            CameraCharacteristics c = manager.getCameraCharacteristics(id);
            Integer facing = c.get(CameraCharacteristics.LENS_FACING);
            if (facing == null || facing != CameraCharacteristics.LENS_FACING_BACK) continue;
            StreamConfigurationMap map = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (map == null) continue;
            Size[] sizes = map.getOutputSizes(MediaRecorder.class);
            if (sizes == null) continue;
            for (Size s : sizes) {
                if (s.getWidth() < 320 || s.getHeight() < 240 || s.getWidth() > 1280 || s.getHeight() > 720) continue;
                if (chosen == null || s.getWidth() * s.getHeight() > chosen.getWidth() * chosen.getHeight()) {
                    selected = id;
                    chosen = s;
                }
            }
            if (selected != null) break;
        }
        if (selected == null) throw new IllegalStateException("No back-camera recorder size at or below 720p");
        cameraId = selected;
        size = chosen;
    }

    private final CameraDevice.StateCallback cameraCallback = new CameraDevice.StateCallback() {
        @Override public void onOpened(CameraDevice device) {
            if (!requested) { device.close(); return; }
            camera = device;
            try { startSegment(); } catch (Exception e) { fail("Recording setup failed: " + e.getMessage()); }
        }
        @Override public void onDisconnected(CameraDevice device) {
            device.close();
            if (camera == device) camera = null;
            if (requested) fail("Camera disconnected");
        }
        @Override public void onError(CameraDevice device, int error) {
            device.close();
            if (camera == device) camera = null;
            if (requested) fail("Camera error " + error);
        }
    };

    private void startSegment() throws CameraAccessException, IOException {
        if (!requested || camera == null) return;
        if (new StatFs(directory().getAbsolutePath()).getAvailableBytes() < MIN_FREE_BYTES)
            throw new IOException("Storage reserve below 200 MiB");
        policy.prune(directory(), null);
        partial = new File(directory(), String.format(java.util.Locale.US, "station-%013d-%03d.partial", System.currentTimeMillis(), fileSequence++ % 1000));
        // Source: https://developer.android.com/reference/android/media/MediaRecorder
        recorder = new MediaRecorder(this);
        recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        recorder.setVideoSize(size.getWidth(), size.getHeight());
        recorder.setVideoFrameRate(24);
        recorder.setVideoEncodingBitRate(2_000_000);
        recorder.setMaxDuration(300_000);
        recorder.setMaxFileSize(MAX_SEGMENT_BYTES);
        recorder.setOutputFile(partial.getAbsolutePath());
        int current = ++generation;
        recorder.setOnInfoListener((r, what, extra) -> {
            if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED || what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED)
                handler.post(() -> { if (current == generation && requested) fail("Recorder safety limit reached before scheduled rollover"); });
        });
        recorder.setOnErrorListener((r, what, extra) -> handler.post(() -> {
            if (current == generation && requested) fail("MediaRecorder error " + what + "/" + extra);
        }));
        recorder.prepare();
        Surface surface = recorder.getSurface();
        // Source: https://developer.android.com/reference/android/hardware/camera2/params/SessionConfiguration
        CameraCaptureSession.StateCallback callback = new CameraCaptureSession.StateCallback() {
            @Override public void onConfigured(CameraCaptureSession configured) {
                if (!requested || current != generation) { configured.close(); return; }
                session = configured;
                try {
                    CaptureRequest.Builder builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
                    builder.addTarget(surface);
                    configured.setRepeatingRequest(builder.build(), null, handler);
                    recorder.start();
                    recording = true;
                    status("recording", "Recording locally without audio");
                    updateNotification("Recording video locally (no audio)");
                    rotation = () -> { if (current == generation && requested) rotate(); };
                    handler.postDelayed(rotation, SEGMENT_MS);
                } catch (Exception e) { fail("Could not start video: " + e.getMessage()); }
            }
            @Override public void onConfigureFailed(CameraCaptureSession failed) {
                failed.close();
                if (current == generation && requested) fail("Camera recording session configuration failed");
            }
        };
        camera.createCaptureSession(new SessionConfiguration(SessionConfiguration.SESSION_REGULAR,
            Collections.singletonList(new OutputConfiguration(surface)), handler::post, callback));
    }

    private void rotate() {
        try {
            finishSegment();
            startSegment();
        } catch (Exception e) { fail("Recording stopped: " + e.getMessage()); }
    }

    private void finishSegment() throws IOException {
        if (rotation != null) handler.removeCallbacks(rotation);
        rotation = null;
        generation++;
        boolean valid = false;
        if (recorder != null) {
            try {
                if (recording) { recorder.stop(); valid = true; }
            } catch (RuntimeException e) {
                throw new IOException("Failed to finalize MP4", e);
            } finally {
                recording = false;
                recorder.reset();
                recorder.release();
                recorder = null;
            }
        }
        if (session != null) { session.close(); session = null; }
        if (partial != null) {
            File result = new File(partial.getParentFile(), partial.getName().replace(".partial", ".mp4"));
            if (valid && partial.length() > 0 && !partial.renameTo(result)) throw new IOException("Cannot finalize segment file");
            if (!valid || partial.exists()) partial.delete();
            partial = null;
        }
        policy.prune(directory(), null);
    }

    private boolean release(boolean finalizeVideo) {
        boolean success = true;
        if (rotation != null) handler.removeCallbacks(rotation);
        rotation = null;
        generation++;
        try {
            if (finalizeVideo) finishSegment();
        } catch (Exception e) { success = false; status("error", "Failed to finalize video: " + e.getMessage()); }
        if (session != null) { session.close(); session = null; }
        if (recorder != null) {
            try { recorder.reset(); recorder.release(); } catch (RuntimeException ignored) { }
            recorder = null;
        }
        recording = false;
        if (partial != null) { partial.delete(); partial = null; }
        if (camera != null) { camera.close(); camera = null; }
        return success;
    }

    private void fail(String detail) {
        requested = false;
        release(true);
        status("error", detail);
        updateNotification("Recording stopped — " + detail);
        // Remain foreground with an explicit visible error until the user taps Stop.
    }

    private File directory() throws IOException {
        File dir = new File(getFilesDir(), "security-recordings");
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Cannot create recording directory");
        return dir;
    }
    private void cleanupOrphans(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) if (f.isFile() && f.getName().matches("station-[0-9]+-[0-9]+\\.partial")) f.delete();
    }
    private void status(String state, String detail) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_STATE, state).putString(KEY_DETAIL, detail).apply();
    }
    private Notification notification(String message) {
        Intent stop = new Intent(this, RecorderService.class).setAction(ACTION_STOP);
        PendingIntent action = PendingIntent.getService(this, 0, stop, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.presence_video_online)
            .setContentTitle("TCL Station camera").setContentText(message).setStyle(new Notification.BigTextStyle().bigText(message))
            .setOngoing(true).addAction(new Notification.Action.Builder(
                Icon.createWithResource(this, android.R.drawable.ic_media_pause), "Stop recording", action).build()).build();
    }
    private void updateNotification(String message) {
        getSystemService(NotificationManager.class).notify(NOTIFICATION_ID, notification(message));
    }
    @Override public void onDestroy() {
        if (handler != null) {
            handler.post(() -> { requested = false; release(true); thread.quitSafely(); });
        }
        super.onDestroy();
    }
}
