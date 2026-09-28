package nl.loc.data.weather;

import java.time.Duration;
import java.time.Instant;

/**
 * Decides when a slot may be looked at again.
 *
 * Open-Meteo's hourly forecast reaches sixteen days counting today, and its limit is a
 * calendar date rather than an exact offset. Eligibility therefore stops a day short, so a
 * slot at the edge cannot be due by our clock and out of range by the service's.
 *
 * A forecast is never refreshed more than once a day. Recovering from a failure is not a
 * refresh, so that case is allowed to come back sooner.
 */
public final class WeatherSchedule {

    public static final Duration FORECAST_WINDOW = Duration.ofDays(14);

    private static final Duration FAR_STEP = Duration.ofDays(3);
    private static final Duration NEAR_STEP = Duration.ofDays(2);
    private static final Duration DAILY_STEP = Duration.ofDays(1);
    private static final Duration FIRST_FAILURE_BACKOFF = Duration.ofHours(1);
    private static final Duration MAX_FAILURE_BACKOFF = Duration.ofHours(24);
    private static final int MAX_BACKOFF_DOUBLINGS = 5;

    private WeatherSchedule() {
    }

    public static boolean withinWindow(Instant now, Instant startsAt) {
        return startsAt.isAfter(now) && !startsAt.isAfter(now.plus(FORECAST_WINDOW));
    }

    /** Checks are spaced wider while the event is still far out, since little changes in a day. */
    public static Instant afterForecast(Instant now, Instant startsAt) {
        long daysUntilStart = Duration.between(now, startsAt).toDays();
        Duration step = daysUntilStart > 7 ? FAR_STEP : daysUntilStart >= 3 ? NEAR_STEP : DAILY_STEP;
        return beforeStart(now.plus(step), startsAt);
    }

    public static Instant afterHourUnavailable(Instant now, Instant startsAt) {
        return beforeStart(now.plus(DAILY_STEP), startsAt);
    }

    /** Waits until the slot actually enters the window instead of asking again in the meantime. */
    public static Instant afterOutsideRange(Instant now, Instant startsAt) {
        Instant entersWindow = startsAt.minus(FORECAST_WINDOW);
        return beforeStart(entersWindow.isAfter(now) ? entersWindow : now.plus(DAILY_STEP), startsAt);
    }

    /** Error recovery rather than a refresh, so this may land sooner than the daily limit. */
    public static Instant afterFailure(Instant now, Instant startsAt, int attempts) {
        int doublings = Math.min(Math.max(attempts, 1) - 1, MAX_BACKOFF_DOUBLINGS);
        Duration backoff = FIRST_FAILURE_BACKOFF.multipliedBy(1L << doublings);
        if (backoff.compareTo(MAX_FAILURE_BACKOFF) > 0) {
            backoff = MAX_FAILURE_BACKOFF;
        }
        return beforeStart(now.plus(backoff), startsAt);
    }

    /** Null means no further check is due: the event would already have started. */
    private static Instant beforeStart(Instant candidate, Instant startsAt) {
        return candidate.isBefore(startsAt) ? candidate : null;
    }
}
