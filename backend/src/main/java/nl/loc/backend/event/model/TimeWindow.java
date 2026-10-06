package nl.loc.backend.event.model;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;

/** Amsterdam calendar windows. The search implementation receives these instants. */
public final class TimeWindow {

    public static final ZoneId ZONE = ZoneId.of("Europe/Amsterdam");

    private TimeWindow() {
    }

    public static Range tonight(Instant now) {
        LocalDate today = now.atZone(ZONE).toLocalDate();
        return new Range(
                today.atTime(18, 0).atZone(ZONE).toInstant(),
                today.plusDays(1).atStartOfDay(ZONE).toInstant(),
                now);
    }

    /**
     * Saturday 00:00 through Monday 00:00. Monday to Friday uses the coming Saturday.
     * Saturday and Sunday use the Saturday already in progress. Ended events drop out
     * because the range still requires the occurrence to end after {@code now}.
     */
    public static Range weekend(Instant now) {
        ZonedDateTime local = now.atZone(ZONE);
        LocalDate today = local.toLocalDate();
        LocalDate saturday = today.getDayOfWeek().getValue() <= DayOfWeek.FRIDAY.getValue()
                ? today.with(TemporalAdjusters.next(DayOfWeek.SATURDAY))
                : today.with(TemporalAdjusters.previousOrSame(DayOfWeek.SATURDAY));
        return new Range(
                saturday.atStartOfDay(ZONE).toInstant(),
                saturday.plusDays(2).atStartOfDay(ZONE).toInstant(),
                now);
    }

    public static Range future(Instant now) {
        return new Range(now, null, now);
    }

    public static Range custom(LocalDate from, LocalDate to, Instant now) {
        return new Range(from.atStartOfDay(ZONE).toInstant(), to.plusDays(1).atStartOfDay(ZONE).toInstant(), now);
    }

    public static Range forPreset(When when, Instant now) {
        return switch (when) {
            case TONIGHT -> tonight(now);
            case WEEKEND -> weekend(now);
            case UPCOMING -> future(now);
        };
    }

    /**
     * @param startsFrom    inclusive lower bound on start, or null for no lower bound
     * @param startsBefore  exclusive upper bound on start, or null for no upper bound
     * @param endsAfter     occurrence must end after this instant
     */
    public record Range(Instant startsFrom, Instant startsBefore, Instant endsAfter) {
    }
}
