package com.aero.tclstation;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Dependency-free decoder for the bounded Open-Meteo current object. */
public final class WeatherModel {
    public final boolean available;
    public final int temperatureF;
    public final String description;
    public final String observedTime;
    public final LocalDateTime observedAt;
    public final int weatherCode;
    public final Integer feelsLikeF;
    public final Integer humidity;
    public final Integer windMph;
    public final boolean isDay;

    private WeatherModel(boolean available, int temperatureF, String description, String observedTime,
                         LocalDateTime observedAt, int weatherCode, Integer feelsLikeF,
                         Integer humidity, Integer windMph, boolean isDay) {
        this.available = available;
        this.temperatureF = temperatureF;
        this.description = description;
        this.observedTime = observedTime;
        this.observedAt = observedAt;
        this.weatherCode = weatherCode;
        this.feelsLikeF = feelsLikeF;
        this.humidity = humidity;
        this.windMph = windMph;
        this.isDay = isDay;
    }

    public static WeatherModel parse(String json) {
        WeatherModel missing = new WeatherModel(false, 0, "Weather unavailable", "", null, -1, null, null, null, false);
        if (json == null || json.length() > 100_000) return missing;
        Matcher current = Pattern.compile("\"current\"\\s*:\\s*\\{([^{}]*)\\}").matcher(json);
        if (!current.find()) return missing;
        String object = current.group(1);
        Matcher temp = Pattern.compile("\"temperature_2m\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)\\s*[,}]").matcher(object + "}");
        Matcher code = Pattern.compile("\"weather_code\"\\s*:\\s*(\\d+)\\s*[,}]").matcher(object + "}");
        Matcher time = Pattern.compile("\"time\"\\s*:\\s*\"(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2})\"").matcher(object);
        if (!temp.find() || !code.find() || !time.find()) return missing;
        try {
            double value = Double.parseDouble(temp.group(1));
            int weatherCode = Integer.parseInt(code.group(1));
            Integer daylight = optional(object, "is_day", 0, 1);
            if (value < -100 || value > 140 || daylight == null
                || "Weather conditions unavailable".equals(description(weatherCode))) return missing;
            LocalDateTime observed = LocalDateTime.parse(time.group(1));
            return new WeatherModel(true, (int) Math.round(value), description(weatherCode),
                time.group(1).substring(11), observed, weatherCode,
                optional(object, "apparent_temperature", -100, 140),
                optional(object, "relative_humidity_2m", 0, 100),
                optional(object, "wind_speed_10m", 0, 250), daylight == 1);
        } catch (NumberFormatException | DateTimeParseException e) { return missing; }
    }

    private static Integer optional(String object, String key, int low, int high) {
        Matcher match = Pattern.compile("\"" + key + "\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)\\s*[,}]").matcher(object + "}");
        if (!match.find()) return null;
        try {
            double value = Double.parseDouble(match.group(1));
            return value >= low && value <= high ? (int) Math.round(value) : null;
        } catch (NumberFormatException e) { return null; }
    }

    static String formatMetric(Integer number, String unit) { return number == null ? "—" : number + unit; }

    public static String description(int code) {
        if (code == 0) return "Clear sky";
        if (code == 1 || code == 2) return "Partly cloudy";
        if (code == 3) return "Overcast";
        if (code == 45 || code == 48) return "Fog";
        if (code >= 51 && code <= 57) return "Drizzle";
        if (code >= 61 && code <= 67) return "Rain";
        if (code >= 71 && code <= 77) return "Snow";
        if (code >= 80 && code <= 82) return "Rain showers";
        if (code == 85 || code == 86) return "Snow showers";
        if (code >= 95 && code <= 99) return "Thunderstorm";
        return "Weather conditions unavailable";
    }
}
