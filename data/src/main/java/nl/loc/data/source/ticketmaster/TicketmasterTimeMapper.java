package nl.loc.data.source.ticketmaster;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.function.Function;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import nl.loc.data.event.DateStatus;
import nl.loc.data.event.EventTimeSlot;
import nl.loc.data.event.TimeStatus;

/** Preserves unknown dates and times instead of publishing provider placeholders as exact times. */
@Slf4j
final class TicketmasterTimeMapper {
    private TicketmasterTimeMapper() {
    }

    static List<EventTimeSlot> map(JsonNode root, JsonNode venue) {
        JsonNode dates = root.path("dates");
        if (!dates.path("start").isObject() && !dates.path("end").isObject()) {
            return List.of();
        }
        ZoneId zone = parse(dates.path("timezone"), ZoneId::of);
        if (zone == null) {
            zone = parse(venue.path("timezone"), ZoneId::of);
        }
        Point start = mapPoint(dates.path("start"), zone);
        Point end = mapPoint(dates.path("end"), zone);
        return List.of(new EventTimeSlot(start.instant(), end.instant(),
                start.date(), start.time(), end.date(), end.time(),
                zone == null ? null : zone.getId(),
                start.dateStatus(), start.timeStatus(), end.dateStatus(), end.timeStatus(),
                dates.path("end").path("approximate").asBoolean(false)));
    }

    private static Point mapPoint(JsonNode node, ZoneId zone) {
        Instant instant = parse(node.path("dateTime"), Instant::parse);
        LocalDate date = parse(node.path("localDate"), LocalDate::parse);
        LocalTime time = parse(node.path("localTime"), LocalTime::parse);
        if (instant != null && zone != null) {
            if (date == null) {
                date = instant.atZone(zone).toLocalDate();
            }
            if (time == null) {
                time = instant.atZone(zone).toLocalTime();
            }
        }

        DateStatus dateStatus;
        if (node.path("dateTBD").asBoolean(false)) {
            dateStatus = DateStatus.TBD;
        } else if (node.path("dateTBA").asBoolean(false)) {
            dateStatus = DateStatus.TBA;
        } else {
            dateStatus = date != null || instant != null ? DateStatus.KNOWN : DateStatus.UNKNOWN;
        }

        TimeStatus timeStatus;
        if (node.path("noSpecificTime").asBoolean(false)) {
            timeStatus = TimeStatus.UNSPECIFIED;
        } else if (node.path("timeTBA").asBoolean(false)) {
            timeStatus = TimeStatus.TBA;
        } else {
            timeStatus = time != null || instant != null ? TimeStatus.KNOWN : TimeStatus.UNKNOWN;
        }

        if (dateStatus != DateStatus.KNOWN) {
            date = null;
            instant = null;
        }
        if (timeStatus != TimeStatus.KNOWN) {
            time = null;
            instant = null;
        }
        if (instant == null && date != null && time != null && zone != null) {
            LocalDateTime local = LocalDateTime.of(date, time);
            List<ZoneOffset> offsets = zone.getRules().getValidOffsets(local);
            // Do not guess during a daylight-saving gap or overlap.
            if (offsets.size() == 1) {
                instant = local.toInstant(offsets.getFirst());
            }
        }
        return new Point(instant, date, time, dateStatus, timeStatus);
    }

    private static <T> T parse(JsonNode node, Function<String, T> parser) {
        if (!node.isTextual() || node.asText().isBlank()) {
            return null;
        }
        try {
            return parser.apply(node.asText().strip());
        } catch (DateTimeException exception) {
            log.warn("Ignoring invalid Ticketmaster date, time or timezone value={}", node.asText());
            return null;
        }
    }

    private record Point(Instant instant, LocalDate date, LocalTime time,
                         DateStatus dateStatus, TimeStatus timeStatus) {
    }
}
