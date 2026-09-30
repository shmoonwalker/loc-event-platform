package nl.loc.data.event;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;

public record EventTimeSlot(
        Instant startsAt, Instant endsAt,
        LocalDate localStartDate, LocalTime localStartTime,
        LocalDate localEndDate, LocalTime localEndTime,
        String timezone,
        DateStatus startDateStatus, TimeStatus startTimeStatus,
        DateStatus endDateStatus, TimeStatus endTimeStatus,
        boolean endApproximate
) {
    /** Existing sources can provide exact instants without claiming a local timezone. */
    public EventTimeSlot(Instant startsAt, Instant endsAt) {
        this(startsAt, endsAt, null, null, null, null, null,
                startsAt == null ? DateStatus.UNKNOWN : DateStatus.KNOWN,
                startsAt == null ? TimeStatus.UNKNOWN : TimeStatus.KNOWN,
                endsAt == null ? DateStatus.UNKNOWN : DateStatus.KNOWN,
                endsAt == null ? TimeStatus.UNKNOWN : TimeStatus.KNOWN, false);
    }

    /** Retains a fixed source offset; differing offsets use UTC so both endpoints stay consistent. */
    public static EventTimeSlot fromOffsetDateTimes(OffsetDateTime start, OffsetDateTime end) {
        if (start != null && end != null && !start.getOffset().equals(end.getOffset())) {
            start = start.withOffsetSameInstant(java.time.ZoneOffset.UTC);
            end = end.withOffsetSameInstant(java.time.ZoneOffset.UTC);
        }
        return new EventTimeSlot(
                start == null ? null : start.toInstant(),
                end == null ? null : end.toInstant(),
                start == null ? null : start.toLocalDate(),
                start == null ? null : start.toLocalTime(),
                end == null ? null : end.toLocalDate(),
                end == null ? null : end.toLocalTime(),
                start == null ? (end == null ? null : end.getOffset().getId()) : start.getOffset().getId(),
                start == null ? DateStatus.UNKNOWN : DateStatus.KNOWN,
                start == null ? TimeStatus.UNKNOWN : TimeStatus.KNOWN,
                end == null ? DateStatus.UNKNOWN : DateStatus.KNOWN,
                end == null ? TimeStatus.UNKNOWN : TimeStatus.KNOWN,
                false);
    }
}
