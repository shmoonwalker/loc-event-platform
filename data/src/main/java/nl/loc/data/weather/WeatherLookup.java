package nl.loc.data.weather;

import java.math.BigDecimal;

/**
 * What one Open-Meteo lookup produced. Only answers are returned; trouble reaching the service
 * is thrown instead, because an answer and a failure lead to different retry behaviour.
 */
public sealed interface WeatherLookup {

    /** Rain chance and wind can be missing on their own, so both are nullable. */
    record Forecast(BigDecimal temperatureCelsius, Integer precipitationProbabilityPercent,
                    BigDecimal windSpeedKmh, int weatherCode) implements WeatherLookup {
    }

    /** The hour was accepted but carries no usable values yet. Expected, not a failure. */
    record HourUnavailable() implements WeatherLookup {
    }

    /** The hour sits outside the published forecast range. Expected near the window edge. */
    record OutsideForecastRange(String reason) implements WeatherLookup {
    }
}
