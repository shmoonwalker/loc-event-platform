package nl.loc.backend.event.search;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import nl.loc.backend.category.dto.response.CategoryCount;
import nl.loc.backend.category.model.EventCategory;
import nl.loc.backend.city.model.City;
import nl.loc.backend.event.dto.response.EventCard;
import nl.loc.backend.event.dto.response.EventPage;
import nl.loc.backend.event.model.BrowseCriteria;
import nl.loc.backend.event.model.EventSort;
import nl.loc.backend.event.model.TimeWindow;
import nl.loc.backend.tag.dto.response.TagChip;
import nl.loc.backend.tag.model.EventTag;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "loc.search.engine", havingValue = "postgres", matchIfMissing = true)
public class PostgresPublicEventSearch implements PublicEventSearch {

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

    public PostgresPublicEventSearch(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public EventPage search(BrowseCriteria criteria, Instant now) {
        TimeWindow.Range range = criteria.when() == null
                ? TimeWindow.future(now)
                : TimeWindow.forPreset(criteria.when(), now);
        EventSort sort = criteria.sort() == EventSort.RELEVANCE && criteria.q() == null
                ? EventSort.START_TIME
                : criteria.sort();
        long total = count(criteria, range);
        List<EventCard> items = total == 0 ? List.of() : cards(criteria, range, sort);
        int totalPages = total == 0 ? 0 : (int) Math.ceil(total / (double) criteria.size());
        return new EventPage(criteria.page(), criteria.size(), total, totalPages, items);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryCount> categories(String citySlug, Instant now) {
        String sql = """
                WITH next_occurrence AS (
                    SELECT DISTINCT ON (s.loc_event_id)
                           coalesce(s.payload->'categories', '[]'::jsonb) AS categories
                    FROM publication.discoverable_events s
                    WHERE %s
                    ORDER BY s.loc_event_id, %s
                )
                SELECT category_name, count(*) AS event_count
                FROM next_occurrence
                CROSS JOIN LATERAL jsonb_array_elements_text(categories) AS category_name
                GROUP BY category_name
                """.formatted(occurrenceFilter(citySlug != null, false, false), STARTS);
        var rows = bind(jdbc.sql(sql), citySlug, TimeWindow.facets(now), List.of(), List.of(), null)
                .query((rs, row) -> new NameCount(rs.getString("category_name"), rs.getLong("event_count")))
                .list();
        return java.util.Arrays.stream(EventCategory.values())
                .map(category -> new CategoryCount(
                        category.slug(),
                        category.label(),
                        countFor(rows, category.catalogName())))
                .filter(category -> category.eventCount() > 0)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TagChip> tags(String citySlug, Instant now, int limit) {
        String sql = """
                WITH next_occurrence AS (
                    SELECT DISTINCT ON (s.loc_event_id)
                           coalesce(s.payload->'tags', '[]'::jsonb) AS tags
                    FROM publication.discoverable_events s
                    WHERE %s
                    ORDER BY s.loc_event_id, %s
                )
                SELECT DISTINCT tag_slug
                FROM next_occurrence
                CROSS JOIN LATERAL jsonb_array_elements_text(tags) AS tag_slug
                """.formatted(occurrenceFilter(citySlug != null, false, false), STARTS);
        return bind(jdbc.sql(sql), citySlug, TimeWindow.facets(now), List.of(), List.of(), null)
                .query((rs, row) -> rs.getString("tag_slug"))
                .list()
                .stream()
                .map(EventTag::fromSlug)
                .flatMap(Optional::stream)
                .map(tag -> new TagChip(tag.slug(), tag.label()))
                .sorted(Comparator.comparing(TagChip::label, String.CASE_INSENSITIVE_ORDER))
                .limit(limit)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<City> findCity(String slug) {
        String sql = """
                SELECT trim(s.payload->'location'->>'city') AS city_name
                FROM publication.discoverable_events s
                WHERE %s = :slug
                  AND coalesce(trim(s.payload->'location'->>'city'), '') <> ''
                GROUP BY 1
                ORDER BY count(*) DESC
                LIMIT 1
                """.formatted(CITY_SLUG);
        return jdbc.sql(sql)
                .param("slug", slug)
                .query((rs, row) -> new City(slug, rs.getString("city_name")))
                .optional();
    }

    @Override
    @Transactional(readOnly = true)
    public List<City> suggestCities(String q, int limit) {
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
        return bind(jdbc.sql(sql), null, TimeWindow.facets(Instant.now()), List.of(), List.of(), null)
                .param("q", needle)
                .param("limit", limit)
                .query((rs, row) -> new City(rs.getString("city_slug"), rs.getString("city_name")))
                .list();
    }

    private long count(BrowseCriteria criteria, TimeWindow.Range range) {
        String sql = """
                WITH next_occurrence AS (
                    SELECT DISTINCT ON (s.loc_event_id) s.loc_event_id
                    FROM publication.discoverable_events s
                    WHERE %s
                    ORDER BY s.loc_event_id, %s
                )
                SELECT count(*) FROM next_occurrence
                """.formatted(occurrenceFilter(criteria), STARTS);
        Long total = bind(jdbc.sql(sql), criteria, range).query(Long.class).single();
        return total == null ? 0 : total;
    }

    static String orderBy(EventSort sort) {
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
                           %s AS start_at,
                           %s AS end_at,
                           nullif(trim(s.payload->'location'->>'city'), '') AS city_name,
                           nullif(%s, '') AS city_slug,
                           nullif(trim(s.payload->'location'->>'venue_name'), '') AS venue_name,
                           s.payload->'images'->0->>'url' AS image_url,
                           coalesce(s.payload->'categories', '[]'::jsonb)::text AS categories_json,
                           coalesce(s.payload->'tags', '[]'::jsonb)::text AS tags_json
                           %s
                    FROM publication.discoverable_events s
                    WHERE %s
                    ORDER BY s.loc_event_id, %s
                )
                SELECT * FROM next_occurrence
                ORDER BY %s
                LIMIT :limit OFFSET :offset
                """.formatted(STARTS, ENDS, CITY_SLUG, rankColumn, occurrenceFilter(criteria), STARTS, orderBy(sort));
        return bind(jdbc.sql(sql), criteria, range)
                .param("limit", criteria.size())
                .param("offset", criteria.page() * criteria.size())
                .query(this::card)
                .list();
    }

    private EventCard card(ResultSet rs, int row) throws SQLException {
        List<String> categories = JsonStrings.array(rs.getString("categories_json")).stream()
                .map(EventCategory::fromCatalogName)
                .flatMap(Optional::stream)
                .map(EventCategory::slug)
                .distinct()
                .toList();
        List<String> tags = JsonStrings.array(rs.getString("tags_json")).stream()
                .map(EventTag::fromSlug)
                .flatMap(Optional::stream)
                .map(EventTag::slug)
                .distinct()
                .toList();
        String title = rs.getString("title");
        return new EventCard(
                rs.getObject("loc_event_id", UUID.class),
                title == null ? "" : title,
                instant(rs, "start_at"),
                instant(rs, "end_at"),
                rs.getString("city_slug"),
                rs.getString("city_name"),
                rs.getString("venue_name"),
                blankToNull(rs.getString("image_url")),
                categories,
                tags);
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
        List<String> categories = criteria.categories().stream().map(EventCategory::catalogName).toList();
        List<String> tags = criteria.tags().stream().map(EventTag::slug).toList();
        JdbcClient.StatementSpec bound = bind(statement, criteria.city(), range, categories, tags, criteria.q());
        if (criteria.place() != null) {
            bound = bound.param("place", criteria.place().name());
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

    private static long countFor(List<NameCount> rows, String name) {
        return rows.stream()
                .filter(row -> name.equalsIgnoreCase(row.name()))
                .mapToLong(NameCount::count)
                .findFirst()
                .orElse(0);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static OffsetDateTime offset(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
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

    private record NameCount(String name, long count) {
    }
}
