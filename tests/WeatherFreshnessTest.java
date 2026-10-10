package com.aero.tclstation;

public final class WeatherFreshnessTest {
    private static void check(boolean value, String description) { if (!value) throw new AssertionError(description); }
    public static void main(String[] args) {
        check(!WeatherFreshness.isFresh(0, 1000), "never loaded");
        check(WeatherFreshness.isFresh(1000, 1000 + 14 * 60_000), "recent live response");
        check(!WeatherFreshness.isFresh(1000, 1000 + 16 * 60_000), "stale response");
        check(!WeatherFreshness.isFresh(1000, 500), "clock reset");
        java.time.LocalDateTime at = java.time.LocalDateTime.parse("2026-10-09T10:30");
        check(WeatherFreshness.isObservationFresh(at, at.plusHours(1)), "recent observation");
        check(!WeatherFreshness.isObservationFresh(at, at.plusMinutes(90)), "ninety-minute-old observation");
        check(!WeatherFreshness.isObservationFresh(at, at.plusHours(3)), "old observation");
        check(!WeatherFreshness.isObservationFresh(at, at.minusHours(1)), "future observation");
        String json = "{\"current\":{\"time\":\"2026-10-09T23:50\",\"temperature_2m\":50,\"weather_code\":0,\"is_day\":0},"
            + "\"daily\":{\"time\":[\"2026-10-09\",\"2026-10-10\"],\"temperature_2m_max\":[60,61],\"temperature_2m_min\":[40,41],\"weather_code\":[0,1],\"precipitation_probability_max\":[0,10]}}";
        WeatherForecast beforeMidnight = WeatherForecast.parse(json, java.time.LocalDateTime.parse("2026-10-09T23:55"));
        check(WeatherFreshness.canDisplay(beforeMidnight, 1000, 2000, java.time.LocalDateTime.parse("2026-10-09T23:56")), "current date and recent load");
        check(!WeatherFreshness.canDisplay(beforeMidnight, 1000, 2000, java.time.LocalDateTime.parse("2026-10-10T00:01")), "midnight invalidates yesterday's week");
        check(!WeatherFreshness.canDisplay(beforeMidnight, 1000, 1000 + 16 * 60_000, java.time.LocalDateTime.parse("2026-10-09T23:59")), "expired loaded card cannot show stale conditions");
        String oldText = "50°F Clear sky";
        check(oldText.equals(WeatherFreshness.visibleSummary(beforeMidnight, 1000, 2000,
            java.time.LocalDateTime.parse("2026-10-09T23:56"), false, oldText)), "fresh card/voice summary");
        check(!WeatherFreshness.visibleSummary(beforeMidnight, 1000, 1000 + 16 * 60_000,
            java.time.LocalDateTime.parse("2026-10-09T23:59"), false, oldText).contains("50°F"), "expired card/voice cannot present old temperature");
        check(!WeatherFreshness.visibleSummary(beforeMidnight, 1000, 2000,
            java.time.LocalDateTime.parse("2026-10-10T00:01"), false, oldText).contains("50°F"), "midnight card/voice cannot present yesterday's temperature");
        check(!WeatherFreshness.visibleSummary(beforeMidnight, 1000, 2000,
            java.time.LocalDateTime.parse("2026-10-09T23:56"), true, oldText).contains("50°F"), "loading card/voice cannot present old temperature");
        System.out.println("PASS WeatherFreshnessTest");
    }
}
