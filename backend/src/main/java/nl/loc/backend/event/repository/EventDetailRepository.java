package nl.loc.backend.event.repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import nl.loc.backend.event.model.EventCategory;
import nl.loc.backend.event.dto.response.EventDetail;
import nl.loc.backend.event.dto.response.FilterOption;
import nl.loc.backend.event.dto.response.OrganizerDetail;
import nl.loc.backend.event.dto.response.OrganizerInfo;
import nl.loc.backend.event.model.Place;
import nl.loc.backend.event.model.EventTag;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Event and organizer detail reads over {@code publication.discoverable_events}; saved events also over the snapshots. */
@Repository
public class EventDetailRepository {

    private static final String STARTS = "(s.payload->'schedule'->>'starts_at')::timestamptz";
    private static final String ENDS = "COALESCE((s.payload->'schedule'->>'ends_at')::timestamptz, " + STARTS + ")";

    private final JdbcClient jdbc;
    private final JsonMapper json;
    private final PublicEventRepository events;

    public EventDetailRepository(JdbcClient jdbc, JsonMapper json, PublicEventRepository events) {
        this.jdbc = jdbc;
        this.json = json;
        this.events = events;
    }

    /** One show: payload of the next upcoming performance is shown; every upcoming date is listed. */
    @Transactional(readOnly = true)
    public Optional<EventDetail> findEvent(UUID id, Instant now, UUID viewerId) {
        List<Row> rows = rows("""
                SELECT s.loc_group_id, s.payload::text AS payload, %s AS start_at, %s AS end_at,
                       %s AS saved, true AS available
                FROM publication.discoverable_events s
                WHERE s.loc_group_id = %s AND %s IS NOT NULL AND %s > :now
                ORDER BY start_at, s.loc_occurrence_id
                """.formatted(STARTS, ENDS, PublicEventRepository.SAVED, PublicEventRepository.SHOW_OF, STARTS, ENDS),
                id, now, viewerId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(detail(rows.getFirst(), rows, true));
    }

    /**
     * A saved show, also when withdrawn or over, and only for the member who saved it.
     * Shows its upcoming available dates; when there are none, all dates with the latest one's payload.
     */
    @Transactional(readOnly = true)
    public Optional<EventDetail> findSavedEvent(UUID id, Instant now, UUID viewerId) {
        List<Row> rows = rows("""
                SELECT s.loc_group_id, s.payload::text AS payload, %1$s AS start_at, %2$s AS end_at, true AS saved,
                       %3$s AND %2$s > :now AS available
                FROM publication.event_snapshot s
                WHERE s.loc_group_id = %5$s AND %1$s IS NOT NULL AND %4$s
                ORDER BY start_at, s.loc_occurrence_id
                """.formatted(STARTS, ENDS, PublicEventRepository.AVAILABLE, PublicEventRepository.SAVED,
                        PublicEventRepository.SHOW_OF),
                id, now, viewerId);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        List<Row> available = rows.stream().filter(Row::available).toList();
        return Optional.of(available.isEmpty()
                ? detail(rows.getLast(), rows, false)
                : detail(available.getFirst(), available, true));
    }

    private List<Row> rows(String sql, UUID id, Instant now, UUID viewerId) {
        return jdbc.sql(sql)
                .param("id", id)
                .param("now", OffsetDateTime.ofInstant(now, java.time.ZoneOffset.UTC))
                .param("viewerId", viewerId)
                .query((rs, row) -> new Row(rs.getObject("loc_group_id", UUID.class), rs.getString("payload"),
                        rs.getObject("start_at", OffsetDateTime.class).toInstant(),
                        rs.getObject("end_at", OffsetDateTime.class).toInstant(),
                        rs.getBoolean("saved"),
                        rs.getBoolean("available")))
                .list();
    }

    private EventDetail detail(Row shown, List<Row> dates, boolean available) {
        UUID id = shown.showId();
        JsonNode p = json.readTree(shown.payload());
        JsonNode loc = p.path("location");
        String city = text(loc, "city");
        // performances of one show can have different ticket links
        List<EventDetail.Occurrence> occurrences = dates.stream()
                .map(r -> new EventDetail.Occurrence(r.start(), r.end(), text(json.readTree(r.payload()), "sourceUrl")))
                .toList();
        long saveCount = jdbc.sql("""
                SELECT count(DISTINCT se.user_id) FROM app.saved_event se
                WHERE se.event_id IN (SELECT g.loc_event_id FROM publication.event_snapshot g WHERE g.loc_group_id = :id)
                """).param("id", id).query(Long.class).single();
        return new EventDetail(
                id, text(p, "title") == null ? "" : text(p, "title"), text(p, "description"),
                Place.valueOf(text(loc, "type")), text(p, "sourceUrl"),
                text(loc, "venue_name"), text(loc, "address"), text(loc, "postal_code"),
                city == null ? null : slug(city), city,
                loc.path("latitude").isNumber() ? loc.path("latitude").asDouble() : null,
                loc.path("longitude").isNumber() ? loc.path("longitude").asDouble() : null,
                categories(p.path("categories")), tags(p.path("tags")),
                strings(p.path("images"), "url"), organizers(p.path("organizers")), occurrences, weather(p.path("weather")),
                shown.saved(), saveCount, available);
    }

    @Transactional(readOnly = true)
    public Optional<OrganizerDetail> findOrganizer(UUID id, Instant now, int page, int size, UUID viewerId) {
        String match = "[{\"locOrganizerId\":\"" + id + "\"}]";
        List<String> payloads = jdbc.sql("""
                SELECT s.payload::text FROM publication.discoverable_events s
                WHERE s.payload->'organizers' @> CAST(:match AS jsonb)
                ORDER BY s.updated_at DESC LIMIT 1
                """).param("match", match).query(String.class).list();
        if (payloads.isEmpty()) {
            return Optional.empty();
        }
        OrganizerInfo info = organizers(json.readTree(payloads.getFirst()).path("organizers")).stream()
                .filter(o -> o.id().equals(id)).findFirst().orElseThrow();
        // signed out: no follows to look up
        boolean followed = viewerId != null && jdbc.sql("""
                SELECT EXISTS (SELECT 1 FROM app.followed_organizer
                               WHERE user_id = :viewerId AND organizer_id = :id)
                """).param("viewerId", viewerId).param("id", id).query(Boolean.class).single();
        return Optional.of(new OrganizerDetail(info, events.organizerEvents(id, now, page, size, viewerId), followed));
    }

    private static EventDetail.Weather weather(JsonNode w) {
        if (!w.isObject()) {
            return null;
        }
        return new EventDetail.Weather(instant(w, "forecast_fetched_at"), instant(w, "requested_for_hour"),
                w.path("temperature_celsius").isNumber() ? w.path("temperature_celsius").asDouble() : null,
                w.path("precipitation_probability_percent").isNumber() ? w.path("precipitation_probability_percent").asInt() : null,
                w.path("wind_speed_kmh").isNumber() ? w.path("wind_speed_kmh").asDouble() : null,
                w.path("weather_code").isNumber() ? w.path("weather_code").asInt() : null);
    }

    private static Instant instant(JsonNode n, String field) {
        String v = text(n, field);
        return v == null ? null : Instant.parse(v);
    }

    private static String slug(String city) {
        return city.strip().toLowerCase(java.util.Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", "-").replaceAll("^-+|-+$", "");
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.path(field);
        return v.isString() && !v.asString().isBlank() ? v.asString().strip() : null;
    }

    private static List<FilterOption> categories(JsonNode labels) {
        List<FilterOption> out = new ArrayList<>();
        for (JsonNode l : labels) {
            for (EventCategory c : EventCategory.values()) {
                if (c.label().equals(l.asString())) {
                    out.add(new FilterOption(c.slug(), c.label()));
                }
            }
        }
        return out;
    }

    private static List<FilterOption> tags(JsonNode slugs) {
        List<FilterOption> out = new ArrayList<>();
        for (JsonNode s : slugs) {
            EventTag.fromSlug(s.asString()).ifPresent(t -> out.add(new FilterOption(t.slug(), t.label())));
        }
        return out;
    }

    private static List<String> strings(JsonNode array, String field) {
        List<String> out = new ArrayList<>();
        for (JsonNode n : array) {
            String v = text(n, field);
            if (v != null) {
                out.add(v);
            }
        }
        return out;
    }

    private static List<OrganizerInfo> organizers(JsonNode array) {
        List<OrganizerInfo> out = new ArrayList<>();
        for (JsonNode o : array) {
            String id = text(o, "locOrganizerId");
            String name = text(o, "name");
            if (id != null && name != null) {
                out.add(new OrganizerInfo(UUID.fromString(id), name, text(o, "description"), text(o, "site")));
            }
        }
        return out;
    }

    private record Row(UUID showId, String payload, Instant start, Instant end, boolean saved, boolean available) {
    }
}
