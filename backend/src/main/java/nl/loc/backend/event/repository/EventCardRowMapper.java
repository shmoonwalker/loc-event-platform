package nl.loc.backend.event.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;
import nl.loc.backend.event.dto.response.EventCard;
import nl.loc.backend.event.model.Place;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

@Component
public class EventCardRowMapper implements RowMapper<EventCard> {
    @Override
    public EventCard mapRow(ResultSet rs, int row) throws SQLException {
        String title = rs.getString("title");
        return new EventCard(
                rs.getObject("loc_event_id", UUID.class),
                title == null ? "" : title,
                instant(rs, "start_at"),
                instant(rs, "end_at"),
                Place.valueOf(rs.getString("place")),
                rs.getString("city_slug"),
                rs.getString("city_name"),
                rs.getString("venue_name"),
                blankToNull(rs.getString("image_url")));
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
