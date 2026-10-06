package nl.loc.backend.event.repository;

import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import nl.loc.backend.category.model.EventCategory;
import nl.loc.backend.city.model.City;
import nl.loc.backend.event.dto.response.EventCard;
import nl.loc.backend.event.dto.response.EventPage;
import nl.loc.backend.event.model.BrowseCriteria;
import nl.loc.backend.event.model.EventSort;
import nl.loc.backend.event.model.TimeWindow;
import nl.loc.backend.tag.model.EventTag;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Discovery reads over {@code publication.discoverable_events}. */
@Repository
public class PublicEventRepository {

    private static final String STARTS =
            "(s.payload->'schedule'->>'starts_at')::timestamptz";
    private static final String ENDS = """
            COALESCE((s.payload->'schedule'->>'ends_at')::timestamptz, (s.payload->'schedule'->>'starts_at')::timestamptz)""";
    private static final String CITY_SLUG = """
            trim(both '-' from lower(regexp_replace(trim(coalesce(s.payload->'location'->>'city', '')), '[^[:alnum:]]+', '-', 'g')))""";
    private static final String DOCUMENT = """
            to_tsvector('simple', concat_ws(' ',
                s.payload->>'title',
                s.payload->>'description',
                s.payload->'location'->>'venue_name',
                s.payload->'location'->>'city',
                (SELECT string_agg(value, ' ')
                   FROM jsonb_array_elements_text(coalesce(s.payload->'categories', '[]'::jsonb)) AS value),
                (SELECT string_agg(value, ' ')
                   FROM jsonb_array_elements_text(coalesce(s.payload->'tags', '[]'::jsonb)) AS value)))""";

    private final JdbcClient jdbc;
    private final EventCardRowMapper cardMapper;

    public PublicEventRepository(JdbcClient jdbc, EventCardRowMapper cardMapper) {
        this.jdbc = jdbc;
        this.cardMapper = cardMapper;
    }

    @Transactional(readOnly = true)
    public EventPage search(BrowseCriteria criteria, TimeWindow.Range range, EventSort sort) {
        long total = count(criteria, range);
        List<EventCard> items = total == 0 ? List.of() : cards(criteria, range, sort);
        int totalPages = total == 0 ? 0 : (int) Math.ceil(total / (double) criteria.size());
        return new EventPage(criteria.page(), criteria.size(), total, totalPages, items);
    }

    @Transactional(readOnly = true)
    public Optional<City> findCity(String slug) {
        String sql = """
                SELECT trim(s.payload->'location'->>'city') AS city_name
                FROM publication.discoverable_events s
                WHERE %s = :slug
                  AND coalesce(trim(s.payload->'location'->>'city'), '') <> ''
                GROUP BY 1
                ORDER BY count(*) DESC, city_name
                LIMIT 1
                """.formatted(CITY_SLUG);
        return jdbc.sql(sql)
                .param("slug", slug)
                .query((rs, row) -> new City(slug, rs.getString("city_name")))
                .optional();
    }

    @Transactional(readOnly = true)
    public List<City> suggestCities(String q, int limit, TimeWindow.Range range) {
        String needle = cityQuery(q);
        String order = needle == null
                ? "CASE WHEN city_slug = 'amsterdam' THEN 0 ELSE 1 END, event_count DESC, city_name"
                : "event_count DESC, city_name";
        String sql = """
                SELECT city_name, city_slug
                FROM (
                    SELECT trim(s.payload->'location'->>'city') AS city_name,
                           %s AS city_slug,
                           count(DISTINCT s.loc_event_id) AS event_count
                    FROM publication.discoverable_events s
                    WHERE %s
                      AND coalesce(trim(s.payload->'location'->>'city'), '') <> ''
                    GROUP BY 1, 2
                ) cities
                WHERE (:q IS NULL OR city_slug ILIKE '%%' || :q || '%%' OR city_name ILIKE '%%' || :q || '%%')
                ORDER BY %s
                LIMIT :limit
                """.formatted(CITY_SLUG, occurrenceFilter(false, false, false), order);
        return bind(jdbc.sql(sql), null, range, List.of(), List.of(), null)
                .param("q", needle, Types.VARCHAR)
                .param("limit", limit)
                .query((rs, row) -> new City(rs.getString("city_slug"), rs.getString("city_name")))
                .list();
    }

    /** One page of an organizer's upcoming events, one card per event, same occurrence selection as the list. */
    @Transactional(readOnly = true)
    public EventPage organizerEvents(UUID organizerId, Instant now, int page, int size) {
        String match = "[{\"locOrganizerId\":\"" + organizerId + "\"}]";
        OffsetDateTime at = OffsetDateTime.ofInstant(now, ZoneOffset.UTC);
        Long total = jdbc.sql("""
                SELECT count(DISTINCT s.loc_event_id) FROM publication.discoverable_events s
                WHERE s.payload->'organizers' @> CAST(:match AS jsonb)
                  AND %1$s IS NOT NULL AND %2$s > :now
                """.formatted(STARTS, ENDS)).param("match", match).param("now", at).query(Long.class).single();
        if (total == null || total == 0) {
            return new EventPage(page, size, 0, 0, List.of());
        }
        String sql = """
                SELECT * FROM (
                    SELECT DISTINCT ON (s.loc_event_id)
                           s.loc_event_id,
                           s.payload->>'title' AS title,
                           s.payload->'location'->>'type' AS place,
                           %1$s AS start_at,
                           %2$s AS end_at,
                           nullif(trim(s.payload->'location'->>'city'), '') AS city_name,
                           nullif(%3$s, '') AS city_slug,
                           nullif(trim(s.payload->'location'->>'venue_name'), '') AS venue_name,
                           s.payload->'images'->0->>'url' AS image_url
                    FROM publication.discoverable_events s
                    WHERE s.payload->'organizers' @> CAST(:match AS jsonb)
                      AND %1$s IS NOT NULL AND %2$s > :now
                    ORDER BY s.loc_event_id, %1$s, s.loc_occurrence_id
                ) e
                ORDER BY start_at, loc_event_id
                LIMIT :limit OFFSET :offset
                """.formatted(STARTS, ENDS, CITY_SLUG);
        List<EventCard> items = jdbc.sql(sql)
                .param("match", match)
                .param("now", at)
                .param("limit", size)
                .param("offset", (long) page * size)
                .query(cardMapper)
                .list();
        return new EventPage(page, size, total, (int) Math.ceil(total / (double) size), items);
    }

    private long count(BrowseCriteria criteria, TimeWindow.Range range) {
        String sql = """
                SELECT count(DISTINCT s.loc_event_id)
                FROM publication.discoverable_events s
                WHERE %s
                """.formatted(occurrenceFilter(criteria));
        Long total = bind(jdbc.sql(sql), criteria, range).query(Long.class).single();
        return total == null ? 0 : total;
    }

    private static String orderBy(EventSort sort) {
        if (sort == EventSort.RELEVANCE) {
            return "ts_rank(document, websearch_to_tsquery('simple', :q)) DESC, start_at, loc_event_id";
        }
        return "start_at, loc_event_id";
    }

    private List<EventCard> cards(BrowseCriteria criteria, TimeWindow.Range range, EventSort sort) {
        String rankColumn = sort == EventSort.RELEVANCE ? ", " + DOCUMENT + " AS document" : "";
        String sql = """
                WITH next_occurrence AS (
                    SELECT DISTINCT ON (s.loc_event_id)
                           s.loc_event_id,
                           s.payload->>'title' AS title,
                           s.payload->'location'->>'type' AS place,
                           %s AS start_at,
                           %s AS end_at,
                           nullif(trim(s.payload->'location'->>'city'), '') AS city_name,
                           nullif(%s, '') AS city_slug,
                           nullif(trim(s.payload->'location'->>'venue_name'), '') AS venue_name,
                           s.payload->'images'->0->>'url' AS image_url
                           %s
                    FROM publication.discoverable_events s
                    WHERE %s
                    ORDER BY s.loc_event_id, %s, s.loc_occurrence_id
                )
                SELECT * FROM next_occurrence
                ORDER BY %s
                LIMIT :limit OFFSET :offset
                """.formatted(STARTS, ENDS, CITY_SLUG, rankColumn, occurrenceFilter(criteria), STARTS, orderBy(sort));
        return bind(jdbc.sql(sql), criteria, range)
                .param("limit", criteria.size())
                .param("offset", (long) criteria.page() * criteria.size())
                .query(cardMapper)
                .list();
    }

    private static String occurrenceFilter(BrowseCriteria criteria) {
        String filter = occurrenceFilter(
                criteria.city() != null, !criteria.categories().isEmpty(), !criteria.tags().isEmpty());
        if (criteria.q() != null) {
            filter = filter + " AND " + DOCUMENT + " @@ websearch_to_tsquery('simple', :q)";
        }
        if (criteria.place() != null) {
            filter = filter + " AND s.payload->'location'->>'type' = :place";
        }
        if (criteria.timeFrom() != null) {
            String localTime = "(" + STARTS + " AT TIME ZONE :zone)::time";
            String join = criteria.timeFrom().isBefore(criteria.timeTo()) ? " AND " : " OR ";
            filter += " AND (" + localTime + " >= CAST(:timeFrom AS time)" + join
                    + localTime + " < CAST(:timeTo AS time))";
        }
        return filter;
    }

    private static String occurrenceFilter(boolean city, boolean categories, boolean tags) {
        StringBuilder filter = new StringBuilder();
        filter.append("(s.payload->'schedule'->>'starts_at') IS NOT NULL");
        filter.append(" AND (CAST(:startsFrom AS timestamptz) IS NULL OR ");
        filter.append(STARTS);
        filter.append(" >= CAST(:startsFrom AS timestamptz))");
        filter.append(" AND (CAST(:startsBefore AS timestamptz) IS NULL OR ");
        filter.append(STARTS);
        filter.append(" < CAST(:startsBefore AS timestamptz))");
        filter.append(" AND ");
        filter.append(ENDS);
        filter.append(" > CAST(:endsAfter AS timestamptz)");
        if (city) {
            filter.append(" AND ");
            filter.append(CITY_SLUG);
            filter.append(" = :city");
        }
        if (categories) {
            filter.append(" AND jsonb_exists_any(coalesce(s.payload->'categories', '[]'::jsonb), ");
            filter.append("CAST(:categories AS text[]))");
        }
        if (tags) {
            filter.append(" AND jsonb_exists_all(coalesce(s.payload->'tags', '[]'::jsonb), ");
            filter.append("CAST(:tags AS text[]))");
        }
        return filter.toString();
    }

    private JdbcClient.StatementSpec bind(JdbcClient.StatementSpec statement, BrowseCriteria criteria, TimeWindow.Range range) {
        List<String> categories = criteria.categories().stream().map(EventCategory::label).toList();
        List<String> tags = criteria.tags().stream().map(EventTag::slug).toList();
        JdbcClient.StatementSpec bound = bind(statement, criteria.city(), range, categories, tags, criteria.q());
        if (criteria.place() != null) {
            bound = bound.param("place", criteria.place().name());
        }
        if (criteria.timeFrom() != null) {
            bound = bound.param("zone", TimeWindow.ZONE.getId())
                    .param("timeFrom", criteria.timeFrom(), Types.TIME)
                    .param("timeTo", criteria.timeTo(), Types.TIME);
        }
        return bound;
    }

    private JdbcClient.StatementSpec bind(
            JdbcClient.StatementSpec statement,
            String city,
            TimeWindow.Range range,
            List<String> categories,
            List<String> tags,
            String q) {
        JdbcClient.StatementSpec bound = statement
                .param("startsFrom", offset(range.startsFrom()), Types.TIMESTAMP_WITH_TIMEZONE)
                .param("startsBefore", offset(range.startsBefore()), Types.TIMESTAMP_WITH_TIMEZONE)
                .param("endsAfter", offset(range.endsAfter()), Types.TIMESTAMP_WITH_TIMEZONE);
        if (city != null) {
            bound = bound.param("city", city);
        }
        if (!categories.isEmpty()) {
            bound = bound.param("categories", categories.toArray(String[]::new), Types.ARRAY);
        }
        if (!tags.isEmpty()) {
            bound = bound.param("tags", tags.toArray(String[]::new), Types.ARRAY);
        }
        if (q != null) {
            bound = bound.param("q", q);
        }
        return bound;
    }

    private static OffsetDateTime offset(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    private static String cityQuery(String q) {
        if (q == null || q.isBlank()) {
            return null;
        }
        String cleaned = q.strip().toLowerCase(Locale.ROOT)
                .replace("%", "")
                .replace("_", "")
                .replace("\\", "");
        return cleaned.isBlank() ? null : cleaned;
    }

}
