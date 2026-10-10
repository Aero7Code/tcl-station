package com.aero.tclstation;

import java.time.LocalDateTime;

public final class WeatherForecastTest {
    private static void check(boolean value, String why) { if (!value) throw new AssertionError(why); }
    public static void main(String[] args) {
        String json = "{\"current\":{\"time\":\"2026-10-09T10:30\",\"temperature_2m\":54.2,\"weather_code\":61,\"relative_humidity_2m\":73,\"apparent_temperature\":50.2,\"wind_speed_10m\":8.2,\"is_day\":1},"
            + "\"daily\":{\"time\":[\"2026-10-09\",\"2026-10-10\",\"2026-10-11\"],\"temperature_2m_max\":[57,60,55],\"temperature_2m_min\":[38,40,36],\"weather_code\":[61,2,71],\"precipitation_probability_max\":[80,10,50]}}";
        LocalDateTime now = LocalDateTime.parse("2026-10-09T11:00");
        WeatherForecast forecast = WeatherForecast.parse(json, now);
        check(forecast.current.available && forecast.current.weatherCode == 61, "actual current weather code");
        check(forecast.current.humidity == 73 && forecast.current.feelsLikeF == 50 && forecast.current.windMph == 8, "current details");
        check(forecast.days.size() == 3 && forecast.days.get(1).highF == 60 && forecast.days.get(1).lowF == 40, "daily forecast");
        check(forecast.days.get(2).precipitationPercent == 50 && forecast.days.get(2).weatherCode == 71, "daily condition");
        check(WeatherForecast.parse("{\"current\":{\"time\":\"2026-10-09T10:30\",\"temperature_2m\":54,\"weather_code\":61,\"is_day\":1}}", now).days.isEmpty(), "no fabricated forecast");
        check(!WeatherForecast.parse("not json").current.available, "bad current stays unavailable");
        check(WeatherForecast.parse(json.replace("\"2026-10-10\"", "\"invalid-date\""), now).days.isEmpty(), "do not mix mismatched daily rows");
        check(!WeatherForecast.parse(json, now.plusDays(2)).current.available, "old observation cannot become current after download");
        check(WeatherForecast.parse(json.replace("\"2026-10-09\",\"2026-10-10\"", "\"2026-10-08\",\"2026-10-10\""), now).days.isEmpty(), "outdated daily dates cannot be shown as rest of week");
        check(!WeatherForecast.parse(json.replace("\"is_day\":1", "\"is_day\":null"), now).current.available, "missing daylight status cannot paint a guessed scene");
        System.out.println("PASS WeatherForecastTest");
    }
}
