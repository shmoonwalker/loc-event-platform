package nl.loc.data.weather;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** A slot that may carry a forecast: physical, located, and starting at a known hour. */
public record WeatherTarget(long timeSlotId, double latitude, double longitude,
                            Instant startsAt, int attempts) {

    /** Open-Meteo answers per hour, so the start instant is truncated before asking. */
    public Instant requestedHour() {
        return startsAt.truncatedTo(ChronoUnit.HOURS);
    }
}
