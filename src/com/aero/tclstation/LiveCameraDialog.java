package com.aero.tclstation;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.SessionConfiguration;
import android.os.Handler;
import android.os.Looper;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Button;

import java.util.Collections;

/** Foreground-only Camera2 preview. No image reader, recorder, file, or network output. */
final class LiveCameraDialog implements TextureView.SurfaceTextureListener {
    private final Activity activity;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final TextureView texture;
    private final TextView status;
    private final Button switcher;
    private final AlertDialog dialog;
    private CameraDevice camera;
    private CameraCaptureSession session;
    private Surface surface;
    private final CameraOpenGate gate = new CameraOpenGate();
    private final CameraCloseHandoff handoff = new CameraCloseHandoff();
    private boolean closed;
    private boolean front;

    private LiveCameraDialog(Activity activity, Runnable onSpeak) {
        this.activity = activity;
        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(12), dp(8), dp(12), dp(8));
        status = new TextView(activity);
        status.setText("Opening camera… no recording");
        status.setTextSize(16);
        LinearLayout toolbar = new LinearLayout(activity);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(android.view.Gravity.CENTER_VERTICAL);
        toolbar.addView(status, new LinearLayout.LayoutParams(0, -2, 1));
        texture = new TextureView(activity);
        switcher = new Button(activity);
        switcher.setText("Switch camera");
        switcher.setEnabled(false);
        switcher.setOnClickListener(v -> {
            switcher.setEnabled(false);
            CameraDevice previous = camera;
            if (previous != null) handoff.await(previous);
            closeCamera();
            front = !front;
            status.setText("Switching to " + (front ? "front" : "back") + " camera…");
            // close() returns before the hardware is free; wait for this device's onClosed.
            if (previous == null && texture.isAvailable()) open(texture.getSurfaceTexture());
        });
        Button speak = new Button(activity);
        speak.setText("Speak");
        speak.setAllCaps(false);
        speak.setOnClickListener(v -> onSpeak.run());
        toolbar.addView(speak, new LinearLayout.LayoutParams(dp(100), dp(48)));
        toolbar.addView(switcher, new LinearLayout.LayoutParams(dp(150), dp(48)));
        body.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(48)));
        body.addView(texture, new LinearLayout.LayoutParams(dp(620), dp(270)));
        dialog = new AlertDialog.Builder(activity)
            .setTitle("Live camera · view only")
            .setView(body)
            .setNegativeButton("Close camera", (d, which) -> close())
            .create();
        dialog.setOnDismissListener(d -> close());
    }

    static AlertDialog show(Activity activity, Runnable onSpeak) {
        LiveCameraDialog view = new LiveCameraDialog(activity, onSpeak);
        view.dialog.show();
        view.texture.setSurfaceTextureListener(view);
        if (view.texture.isAvailable()) view.open(view.texture.getSurfaceTexture());
        return view.dialog;
    }

    private int dp(int value) {
        return (int) (value * activity.getResources().getDisplayMetrics().density + .5f);
    }

    private void open(SurfaceTexture target) {
        if (closed) return;
        final int token = gate.begin();
        if (token < 0) return;
        try {
            CameraManager manager = activity.getSystemService(CameraManager.class);
            String id = null;
            Size size = null;
            for (String candidate : manager.getCameraIdList()) {
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(candidate);
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                int wanted = front ? CameraCharacteristics.LENS_FACING_FRONT : CameraCharacteristics.LENS_FACING_BACK;
                if (facing == null || facing != wanted) continue;
                StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map == null) continue;
                Size[] choices = map.getOutputSizes(SurfaceTexture.class);
                if (choices == null) continue;
                for (Size s : choices) {
                    if (s.getWidth() > 1280 || s.getHeight() > 720) continue;
                    if (size == null || s.getWidth() * s.getHeight() > size.getWidth() * size.getHeight()) size = s;
                }
                if (size != null) { id = candidate; break; }
            }
            if (id == null) {
                status.setText("No compatible " + (front ? "front" : "back") + " camera available");
                closeCamera();
                switcher.setEnabled(true);
                return;
            }
            target.setDefaultBufferSize(size.getWidth(), size.getHeight());
            surface = new Surface(target);
            manager.openCamera(id, new CameraDevice.StateCallback() {
                @Override public void onOpened(CameraDevice device) {
                    if (closed || !gate.isCurrent(token) || camera != null) { device.close(); return; }
                    camera = device;
                    configure(token);
                }
                @Override public void onDisconnected(CameraDevice device) {
                    device.close();
                    if (!gate.isCurrent(token)) return;
                    status.setText("Camera disconnected");
                    closeCamera();
                    switcher.setEnabled(true);
                }
                @Override public void onError(CameraDevice device, int error) {
                    device.close();
                    if (!gate.isCurrent(token)) return;
                    status.setText("Camera unavailable (error " + error + ")");
                    closeCamera();
                    switcher.setEnabled(true);
                }
                @Override public void onClosed(CameraDevice device) {
                    if (handoff.closed(device) && !closed && texture.isAvailable()) open(texture.getSurfaceTexture());
                }
            }, main);
        } catch (CameraAccessException | SecurityException | IllegalArgumentException e) {
            status.setText("Camera could not open: " + e.getClass().getSimpleName());
            closeCamera();
            switcher.setEnabled(true);
        }
    }

    private void configure(int token) {
        try {
            camera.createCaptureSession(new SessionConfiguration(SessionConfiguration.SESSION_REGULAR,
                Collections.singletonList(new OutputConfiguration(surface)), activity.getMainExecutor(), new CameraCaptureSession.StateCallback() {
                @Override public void onConfigured(CameraCaptureSession configured) {
                    if (closed || !gate.isCurrent(token) || camera == null) { configured.close(); return; }
                    session = configured;
                    try {
                        CaptureRequest.Builder request = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                        request.addTarget(surface);
                        configured.setRepeatingRequest(request.build(), null, main);
                        status.setText("LIVE · " + (front ? "front" : "back") + " camera · nothing saved");
                        switcher.setEnabled(true);
                    } catch (CameraAccessException | IllegalStateException e) {
                        status.setText("Preview could not start");
                        closeCamera();
                        switcher.setEnabled(true);
                    }
                }
                @Override public void onConfigureFailed(CameraCaptureSession failed) {
                    failed.close();
                    if (!gate.isCurrent(token)) return;
                    status.setText("Preview configuration failed");
                    closeCamera();
                    switcher.setEnabled(true);
                }
            }));
        } catch (CameraAccessException | IllegalStateException e) {
            status.setText("Preview configuration failed");
            closeCamera();
            switcher.setEnabled(true);
        }
    }

    private void closeCamera() {
        gate.invalidate();
        if (session != null) { session.close(); session = null; }
        if (camera != null) { camera.close(); camera = null; }
        if (surface != null) { surface.release(); surface = null; }
    }

    private void close() {
        closed = true;
        handoff.cancel();
        closeCamera();
    }

    @Override public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) { open(texture); }
    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) { }
    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) { close(); return true; }
    @Override public void onSurfaceTextureUpdated(SurfaceTexture texture) { }
}
