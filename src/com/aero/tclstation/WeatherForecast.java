package com.aero.tclstation;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A single live response: current conditions and real daily forecast, never synthesized. */
public final class WeatherForecast {
    public final WeatherModel current;
    public final List<Day> days;

    public static final class Day {
        public final LocalDate date;
        public final int highF, lowF, weatherCode, precipitationPercent;
        Day(LocalDate date, int highF, int lowF, int weatherCode, int precipitationPercent) {
            this.date = date;
            this.highF = highF;
            this.lowF = lowF;
            this.weatherCode = weatherCode;
            this.precipitationPercent = precipitationPercent;
        }
    }

    private WeatherForecast(WeatherModel current, List<Day> days) {
        this.current = current;
        this.days = Collections.unmodifiableList(days);
    }

    public static WeatherForecast parse(String json) {
        return parse(json, LocalDateTime.now(ZoneId.of("America/Denver")));
    }

    static WeatherForecast parse(String json, LocalDateTime now) {
        WeatherModel current = WeatherModel.parse(json);
        List<Day> days = new ArrayList<>();
        if (!current.available || !WeatherFreshness.isObservationFresh(current.observedAt, now))
            return new WeatherForecast(WeatherModel.parse(null), days);
        if (json == null || json.length() > 100_000) return new WeatherForecast(current, days);
        Matcher daily = Pattern.compile("\"daily\"\\s*:\\s*\\{([^{}]*)\\}").matcher(json);
        if (!daily.find()) return new WeatherForecast(current, days);
        String body = daily.group(1);
        String[] dates = array(body, "time"), high = array(body, "temperature_2m_max"),
            low = array(body, "temperature_2m_min"), codes = array(body, "weather_code"),
            rain = array(body, "precipitation_probability_max");
        if (dates == null || high == null || low == null || codes == null || rain == null
            || dates.length < 2 || dates.length > 7 || high.length != dates.length
            || low.length != dates.length || codes.length != dates.length || rain.length != dates.length)
            return new WeatherForecast(current, days);
        try {
            for (int i = 0; i < dates.length; i++) {
                LocalDate date = LocalDate.parse(dates[i].replace("\"", ""));
                if (i == 0 && !date.equals(now.toLocalDate())) throw new IllegalArgumentException();
                if (i > 0 && !date.equals(days.get(i - 1).date.plusDays(1))) throw new IllegalArgumentException();
                int hi = bounded(high[i], -100, 140), lo = bounded(low[i], -100, 140),
                    code = bounded(codes[i], 0, 99), chance = bounded(rain[i], 0, 100);
                if (hi < lo) throw new IllegalArgumentException();
                days.add(new Day(date, hi, lo, code, chance));
            }
        } catch (IllegalArgumentException | DateTimeParseException badForecast) {
            days.clear();
        }
        return new WeatherForecast(current, days);
    }

    private static String[] array(String body, String key) {
        Matcher match = Pattern.compile("\"" + key + "\"\\s*:\\s*\\[([^\\[\\]]*)\\]").matcher(body);
        return match.find() ? match.group(1).split(",", -1) : null;
    }

    private static int bounded(String value, int low, int high) {
        double number = Double.parseDouble(value.trim());
        if (!Double.isFinite(number) || number < low || number > high) throw new IllegalArgumentException();
        return (int) Math.round(number);
    }
}
