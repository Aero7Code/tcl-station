package com.aero.tclstation;

public final class WeatherModelTest {
    private static int passed;
    private static void check(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
        passed++;
        System.out.println("PASS " + name);
    }
    public static void main(String[] args) throws Exception {
        WeatherModel sunny = WeatherModel.parse("{\"current\":{\"time\":\"2026-10-09T12:15\",\"temperature_2m\":67.6,\"weather_code\":0,\"is_day\":1}}");
        check(sunny.available && sunny.temperatureF == 68, "rounded Fahrenheit temperature");
        check("Clear sky".equals(sunny.description), "clear sky description");
        check("12:15".equals(sunny.observedTime), "observation time");
        check(java.time.LocalDateTime.parse("2026-10-09T12:15").equals(sunny.observedAt), "full observation timestamp");
        check(!WeatherModel.parse("{\"current\":{\"time\":\"2026-10-09T12:15\",\"temperature_2m\":67,\"weather_code\":0}}").available, "missing daylight status cannot paint a guessed sky");
        check(WeatherModel.parse("{\"current\":{\"time\":\"2026-10-09T12:15\",\"temperature_2m\":-4,\"weather_code\":0,\"is_day\":1,\"apparent_temperature\":-12}}").feelsLikeF == -12, "negative apparent temperature is real");
        check("-12°F".equals(WeatherModel.formatMetric(-12, "°F")), "subzero feels-like is shown");
        check("—".equals(WeatherModel.formatMetric(null, "°F")), "missing metric is not guessed");
        check("Rain".equals(WeatherModel.description(63)), "rain code");
        check("Snow".equals(WeatherModel.description(75)), "snow code");
        check("Thunderstorm".equals(WeatherModel.description(95)), "storm code");
        check("Weather conditions unavailable".equals(WeatherModel.description(999)), "unknown condition");
        check(!WeatherModel.parse("{\"current\":{\"time\":\"2026-10-09T12:15\",\"temperature_2m\":67,\"weather_code\":999}}").available, "unknown condition never draws a clear-sky scene");
        check(!WeatherModel.parse("{\"error\":true,\"reason\":\"bad query\"}").available, "API error is unavailable");
        check(!WeatherModel.parse("{\"current\":{\"temperature_2m\":null,\"weather_code\":0}}").available, "null temperature is unavailable");
        check(!WeatherModel.parse("{\"current\":{\"temperature_2m\":999,\"weather_code\":0}}").available, "implausible temperature is unavailable");
        if (args.length > 0) {
            WeatherModel live = WeatherModel.parse(java.nio.file.Files.readString(java.nio.file.Path.of(args[0])));
            check(live.available, "live Open-Meteo response parses");
            System.out.println("Live weather: " + live.temperatureF + "°F " + live.description + " at " + live.observedTime);
        }
        System.out.println("WeatherModelTest: " + passed + " checks passed");
    }
}
