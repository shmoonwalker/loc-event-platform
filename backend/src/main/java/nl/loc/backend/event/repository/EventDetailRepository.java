package nl.loc.backend.event.repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import nl.loc.backend.category.model.EventCategory;
import nl.loc.backend.event.dto.response.EventDetail;
import nl.loc.backend.event.dto.response.FilterOption;
import nl.loc.backend.event.dto.response.OrganizerDetail;
import nl.loc.backend.event.dto.response.OrganizerInfo;
import nl.loc.backend.event.model.Place;
import nl.loc.backend.tag.model.EventTag;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Event and organizer detail reads over {@code publication.discoverable_events}. */
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

    /** Payload of the next upcoming occurrence is shown; every upcoming date is listed. */
    @Transactional(readOnly = true)
    public Optional<EventDetail> findEvent(UUID id, Instant now) {
        List<Row> rows = jdbc.sql("""
                SELECT s.payload::text AS payload, %s AS start_at, %s AS end_at
                FROM publication.discoverable_events s
                WHERE s.loc_event_id = :id AND %s IS NOT NULL AND %s > :now
                ORDER BY start_at, s.loc_occurrence_id
                """.formatted(STARTS, ENDS, STARTS, ENDS))
                .param("id", id)
                .param("now", OffsetDateTime.ofInstant(now, java.time.ZoneOffset.UTC))
                .query((rs, row) -> new Row(rs.getString("payload"),
                        rs.getObject("start_at", OffsetDateTime.class).toInstant(),
                        rs.getObject("end_at", OffsetDateTime.class).toInstant()))
                .list();
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        JsonNode p = json.readTree(rows.getFirst().payload());
        JsonNode loc = p.path("location");
        String city = text(loc, "city");
        List<EventDetail.Occurrence> occurrences = rows.stream()
                .map(r -> new EventDetail.Occurrence(r.start(), r.end())).toList();
        return Optional.of(new EventDetail(
                id, text(p, "title") == null ? "" : text(p, "title"), text(p, "description"),
                Place.valueOf(text(loc, "type")), text(p, "sourceUrl"),
                text(loc, "venue_name"), text(loc, "address"), text(loc, "postal_code"),
                city == null ? null : slug(city), city,
                loc.path("latitude").isNumber() ? loc.path("latitude").asDouble() : null,
                loc.path("longitude").isNumber() ? loc.path("longitude").asDouble() : null,
                categories(p.path("categories")), tags(p.path("tags")),
                strings(p.path("images"), "url"), organizers(p.path("organizers")), occurrences, weather(p.path("weather"))));
    }

    @Transactional(readOnly = true)
    public Optional<OrganizerDetail> findOrganizer(UUID id, Instant now, int page, int size) {
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
        return Optional.of(new OrganizerDetail(info, events.organizerEvents(id, now, page, size)));
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

    private record Row(String payload, Instant start, Instant end) {
    }
}
