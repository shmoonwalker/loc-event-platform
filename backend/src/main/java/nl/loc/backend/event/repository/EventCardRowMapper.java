package nl.loc.backend.event.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import nl.loc.backend.category.model.EventCategory;
import nl.loc.backend.event.dto.response.EventCard;
import nl.loc.backend.tag.model.EventTag;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Component
public class EventCardRowMapper implements RowMapper<EventCard> {
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() { };
    private final JsonMapper mapper;

    public EventCardRowMapper(JsonMapper mapper) {
        this.mapper = mapper;
    }

    private List<String> strings(String json) {
        return json == null ? List.of() : mapper.readValue(json, STRING_LIST);
    }

    @Override
    public EventCard mapRow(ResultSet rs, int row) throws SQLException {
        List<String> categories = strings(rs.getString("categories_json")).stream()
                .map(EventCategory::fromCatalogName)
                .flatMap(Optional::stream)
                .map(EventCategory::slug)
                .distinct()
                .toList();
        List<String> tags = strings(rs.getString("tags_json")).stream()
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

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }
}
