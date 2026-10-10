package com.aero.tclstation;

import java.time.Duration;
import java.time.LocalDateTime;

/** Monotonic freshness gate; a stale scene cannot be called current. */
final class WeatherFreshness {
    private static final long MAX_AGE_MS = 15 * 60_000L;
    static boolean isFresh(long loadedAtElapsedMillis, long nowElapsedMillis) {
        return loadedAtElapsedMillis > 0 && nowElapsedMillis >= loadedAtElapsedMillis
            && nowElapsedMillis - loadedAtElapsedMillis <= MAX_AGE_MS;
    }

    /** Open-Meteo's local current.time can lag the wall clock; do not accept an old feed. */
    static boolean isObservationFresh(LocalDateTime observed, LocalDateTime now) {
        if (observed == null || now == null) return false;
        long age = Duration.between(observed, now).toMillis();
        return age >= -15 * 60_000L && age <= 60 * 60_000L;
    }

    static boolean canDisplay(WeatherForecast forecast, long loadedAt, long nowElapsed, LocalDateTime nowLocal) {
        return forecast != null && forecast.current.available
            && isFresh(loadedAt, nowElapsed)
            && isObservationFresh(forecast.current.observedAt, nowLocal)
            && (forecast.days.isEmpty() || forecast.days.get(0).date.equals(nowLocal.toLocalDate()));
    }

    static String visibleSummary(WeatherForecast forecast, long loadedAt, long nowElapsed,
                                 LocalDateTime nowLocal, boolean loading, String summary) {
        if (loading) return "Weather: loading…";
        if (forecast != null && forecast.current.available
            && !canDisplay(forecast, loadedAt, nowElapsed, nowLocal))
            return "Weather: conditions expired — refreshing";
        return summary;
    }
}
