package nl.loc.backend.user.repository;

import static nl.loc.backend.event.repository.PublicEventRepository.CITY_SLUG;
import static nl.loc.backend.event.repository.PublicEventRepository.ENDS;
import static nl.loc.backend.event.repository.PublicEventRepository.STARTS;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import nl.loc.backend.event.repository.EventCardRowMapper;
import nl.loc.backend.user.dto.response.SavedEventResponse;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class SavedEventRepository {

    // same rule as the publication.discoverable_events view
    private static final String AVAILABLE =
            "(s.discoverable AND (s.payload->>'validUntil')::timestamptz > CURRENT_TIMESTAMP)";

    private final JdbcClient jdbc;
    private final EventCardRowMapper cardMapper;

    public SavedEventRepository(JdbcClient jdbc, EventCardRowMapper cardMapper) {
        this.jdbc = jdbc;
        this.cardMapper = cardMapper;
    }

    public void saveEvent(UUID userId, UUID eventId) {
        jdbc.sql("""
            INSERT INTO app.saved_event (user_id, event_id)
            VALUES (:userId, :eventId)
            ON CONFLICT DO NOTHING
            """)
                .param("userId", userId)
                .param("eventId", eventId)
                .update();
    }

    public void deleteEvent(UUID userId, UUID eventId) {
        jdbc.sql("""
            DELETE FROM app.saved_event
            WHERE user_id = :userId AND event_id = :eventId
            """)
                .param("userId", userId)
                .param("eventId", eventId)
                .update();
    }


    public boolean eventExists(UUID eventId) {
        return jdbc.sql("""
            SELECT EXISTS (
                SELECT 1
                FROM publication.discoverable_events
                WHERE loc_event_id = :eventId
            )
            """)
                .param("eventId", eventId)
                .query(Boolean.class)
                .single();
    }

    public long countSaved(UUID userId) {
        return jdbc.sql("""
            SELECT COUNT(DISTINCT se.event_id)
            FROM app.saved_event se
            JOIN publication.event_snapshot s ON s.loc_event_id = se.event_id
            WHERE se.user_id = :userId
            """)
                .param("userId", userId)
                .query(Long.class)
                .single();
    }

    /** Each saved event shows its next available occurrence, otherwise its latest one. */
    public List<SavedEventResponse> findSaved(UUID userId, int page, int size) {
        String sql = """
            SELECT se.saved_at,
                   s.loc_event_id,
                   s.payload->>'title' AS title,
                   s.payload->'location'->>'type' AS place,
                   %1$s AS start_at,
                   %2$s AS end_at,
                   nullif(trim(s.payload->'location'->>'city'), '') AS city_name,
                   nullif(%3$s, '') AS city_slug,
                   nullif(trim(s.payload->'location'->>'venue_name'), '') AS venue_name,
                   s.payload->'images'->0->>'url' AS image_url,
                   %4$s AS available
            FROM app.saved_event se
            JOIN LATERAL (
                SELECT * FROM publication.event_snapshot s
                WHERE s.loc_event_id = se.event_id
                ORDER BY %4$s DESC, CASE WHEN %4$s THEN %1$s END, %1$s DESC NULLS LAST, s.loc_occurrence_id
                LIMIT 1
            ) s ON true
            WHERE se.user_id = :userId
            ORDER BY se.saved_at DESC, se.event_id
            LIMIT :limit OFFSET :offset
            """.formatted(STARTS, ENDS, CITY_SLUG, AVAILABLE);
        return jdbc.sql(sql)
                .param("userId", userId)
                .param("limit", size)
                .param("offset", (long) page * size)
                .query((rs, row) -> new SavedEventResponse(
                        cardMapper.mapRow(rs, row),
                        rs.getObject("saved_at", OffsetDateTime.class).toInstant(),
                        rs.getBoolean("available")))
                .list();
    }
}
