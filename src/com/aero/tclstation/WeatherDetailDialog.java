package com.aero.tclstation;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Forecast-first full-screen view; no fake weather when the feed is unavailable. */
final class WeatherDetailDialog {
    private static final int WHITE = Color.WHITE;
    private static final int MUTED = 0xffe1eaf0;

    static Dialog show(Activity activity, WeatherForecast forecast, Runnable refresh) {
        Dialog dialog = new Dialog(activity, android.R.style.Theme_Material_NoActionBar);
        FrameLayout frame = new FrameLayout(activity);
        frame.addView(new WeatherSceneView(activity, forecast.current),
            new FrameLayout.LayoutParams(-1, -1));
        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        int margin = dp(activity, 24);
        body.setPadding(margin, dp(activity, 12), margin, dp(activity, 32));
        scroll.addView(body);
        frame.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout toolbar = new LinearLayout(activity);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        body.addView(toolbar);
        TextView title = label(activity, "WEATHER  /  " + StationConfig.NAME.toUpperCase(Locale.US), 17, WHITE, true);
        toolbar.addView(title, new LinearLayout.LayoutParams(0, dp(activity, 52), 1));
        title.setGravity(Gravity.CENTER_VERTICAL);
        Button close = new Button(activity);
        close.setAllCaps(false);
        close.setText("Close");
        close.setContentDescription("Close detailed weather");
        close.setOnClickListener(v -> dialog.dismiss());
        toolbar.addView(close);

        if (!forecast.current.available) {
            label(body, activity, "Current conditions unavailable", 30, WHITE, true);
            label(body, activity, "Check the connection and refresh. No current scene or forecast is inferred from old data.", 17, MUTED, false);
        } else {
            WeatherModel now = forecast.current;
            label(body, activity, now.temperatureF + "°", 72, WHITE, true);
            label(body, activity, now.description, 28, WHITE, true);
            label(body, activity, "Observed at " + now.observedTime + " • configured area", 15, MUTED, false);
            View gap = new View(activity);
            body.addView(gap, new LinearLayout.LayoutParams(1, dp(activity, 28)));
            label(body, activity, "RIGHT NOW", 15, MUTED, true);
            String extra = "Feels like " + WeatherModel.formatMetric(now.feelsLikeF, "°F")
                + "     Humidity " + WeatherModel.formatMetric(now.humidity, "%")
                + "     Wind " + WeatherModel.formatMetric(now.windMph, " mph");
            label(body, activity, extra, 18, WHITE, false);
        }
        Button update = new Button(activity);
        update.setText("Refresh weather & forecast");
        update.setAllCaps(false);
        update.setOnClickListener(v -> refresh.run());
        LinearLayout.LayoutParams updateParams = new LinearLayout.LayoutParams(-1, dp(activity, 52));
        updateParams.topMargin = dp(activity, 22);
        body.addView(update, updateParams);

        View spacer = new View(activity);
        body.addView(spacer, new LinearLayout.LayoutParams(1, dp(activity, 75)));
        label(body, activity, "REST OF THE WEEK", 23, WHITE, true);
        if (forecast.days.size() < 2) {
            label(body, activity, "Forecast unavailable — try Refresh when connected.", 17, MUTED, false);
        } else {
            DateTimeFormatter format = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US);
            for (int i = 1; i < forecast.days.size(); i++) {
                WeatherForecast.Day day = forecast.days.get(i);
                String text = day.date.format(format) + "    " + WeatherModel.description(day.weatherCode)
                    + "\n" + day.highF + "° / " + day.lowF + "°F     Rain " + day.precipitationPercent + "%";
                TextView row = label(body, activity, text, 17, WHITE, false);
                row.setPadding(dp(activity, 12), dp(activity, 13), dp(activity, 12), dp(activity, 13));
                row.setBackgroundColor(0x88303946);
                LinearLayout.LayoutParams position = (LinearLayout.LayoutParams) row.getLayoutParams();
                position.topMargin = dp(activity, 6);
                row.setLayoutParams(position);
            }
        }
        TextView attribution = label(body, activity, "Forecast: Open-Meteo • configured area, not tablet GPS", 13, MUTED, false);
        attribution.setPadding(0, dp(activity, 20), 0, 0);
        dialog.setContentView(frame);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
        return dialog;
    }

    private static int dp(Activity context, int value) { return (int) (value * context.getResources().getDisplayMetrics().density + .5f); }
    private static TextView label(LinearLayout parent, Activity context, String text, int sp, int color, boolean bold) {
        TextView view = label(context, text, sp, color, bold);
        parent.addView(view);
        return view;
    }
    private static TextView label(Activity context, String text, int sp, int color, boolean bold) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(sp);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private static final class WeatherSceneView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final WeatherModel now;
        private boolean animate;
        private long frame;
        WeatherSceneView(Activity activity, WeatherModel conditions) {
            super(activity);
            now = conditions;
            animate = ValueAnimator.areAnimatorsEnabled() && conditions.available;
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }
        @Override protected void onDetachedFromWindow() { animate = false; super.onDetachedFromWindow(); }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth(), h = getHeight();
            int code = now.weatherCode;
            boolean storm = code >= 95, snow = code >= 71 && code <= 77 || code >= 85 && code <= 86;
            boolean rain = code >= 51 && code <= 67 || code >= 80 && code <= 82 || storm;
            boolean cloud = code >= 1 && code <= 3 || code >= 45 && code <= 48 || rain || snow;
            int top = !now.available ? 0xff202e3b : storm ? 0xff182634 : snow ? 0xff415b70
                : rain ? 0xff253f50 : !now.isDay ? 0xff14293e : cloud ? 0xff477287 : 0xff2d80a2;
            int bottom = !now.available ? 0xff34414a : storm ? 0xff455364 : snow ? 0xff87a4b1
                : rain ? 0xff4b687a : !now.isDay ? 0xff3f6171 : cloud ? 0xff82a6ab : 0xffb9cdb2;
            paint.setShader(new LinearGradient(0, 0, 0, h, top, bottom, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, w, h, paint);
            paint.setShader(null);
            if (!now.available) return;
            float motion = animate ? frame : 0;
            if (!cloud) {
                paint.setColor(now.isDay ? 0x88ffe4a4 : 0xaad2e6ef);
                canvas.drawCircle(w * .79f, h * .16f, Math.min(w, h) * .09f, paint);
            }
            if (cloud) {
                paint.setColor(0x777f99a2);
                for (int i = 0; i < 4; i++) {
                    float x = ((i * w * .37f + motion * (i % 2 == 0 ? 3 : 2)) % (w + 220)) - 110;
                    float y = h * (.12f + i * .12f);
                    canvas.drawOval(x, y, x + 200, y + 48, paint);
                }
            }
            if (rain || snow) {
                paint.setColor(snow ? 0xaaffffff : 0x998ed5eb);
                paint.setStrokeWidth(snow ? 3 : 2);
                for (int i = 0; i < 45; i++) {
                    float x = (i * 137f + (snow ? motion * 1.5f : motion * 2.5f)) % Math.max(w, 1);
                    float y = (i * 97f + motion * (snow ? 5 : 12)) % Math.max(h, 1);
                    if (snow) canvas.drawCircle(x, y, 3, paint);
                    else canvas.drawLine(x, y, x - 4, y + 18, paint);
                }
            }
            if (animate) { frame++; postInvalidateDelayed(180); }
        }
    }
}
