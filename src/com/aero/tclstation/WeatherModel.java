package com.aero.tclstation;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Small, dependency-free decoder for Open-Meteo's flat current object. */
public final class WeatherModel {
    public final boolean available;
    public final int temperatureF;
    public final String description;
    public final String observedTime;

    private WeatherModel(boolean available, int temperatureF, String description, String observedTime) {
        this.available = available;
        this.temperatureF = temperatureF;
        this.description = description;
        this.observedTime = observedTime;
    }

    public static WeatherModel parse(String json) {
        WeatherModel missing = new WeatherModel(false, 0, "Weather unavailable", "");
        if (json == null || json.length() > 100_000) return missing;
        Matcher current = Pattern.compile("\\\"current\\\"\\s*:\\s*\\{([^{}]*)\\}").matcher(json);
        if (!current.find()) return missing;
        String object = current.group(1);
        Matcher temp = Pattern.compile("\\\"temperature_2m\\\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)\\s*[,}]").matcher(object + "}");
        Matcher code = Pattern.compile("\\\"weather_code\\\"\\s*:\\s*(\\d+)\\s*[,}]").matcher(object + "}");
        Matcher time = Pattern.compile("\\\"time\\\"\\s*:\\s*\\\"\\d{4}-\\d{2}-\\d{2}T(\\d{2}:\\d{2})\\\"").matcher(object);
        if (!temp.find() || !code.find() || !time.find()) return missing;
        try {
            double value = Double.parseDouble(temp.group(1));
            int weatherCode = Integer.parseInt(code.group(1));
            if (value < -100 || value > 140) return missing;
            return new WeatherModel(true, (int) Math.round(value), description(weatherCode), time.group(1));
        } catch (NumberFormatException e) {
            return missing;
        }
    }

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
