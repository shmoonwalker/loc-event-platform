package nl.loc.backend.user.repository;

import static nl.loc.backend.event.repository.PublicEventRepository.AVAILABLE;
import static nl.loc.backend.event.repository.PublicEventRepository.CITY_SLUG;
import static nl.loc.backend.event.repository.PublicEventRepository.ENDS;
import static nl.loc.backend.event.repository.PublicEventRepository.SHOW_OF;
import static nl.loc.backend.event.repository.PublicEventRepository.STARTS;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import nl.loc.backend.event.repository.EventCardRowMapper;
import nl.loc.backend.user.dto.response.SavedEventResponse;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Rows keep the stable {@code loc_event_id}; the show ({@code loc_group_id}) is looked up when reading. */
@Repository
public class SavedEventRepository {

    private final JdbcClient jdbc;
    private final EventCardRowMapper cardMapper;

    public SavedEventRepository(JdbcClient jdbc, EventCardRowMapper cardMapper) {
        this.jdbc = jdbc;
        this.cardMapper = cardMapper;
    }

    /** Saves one performance of the show, preferring the one the id names; nothing when the show is already saved. */
    public void saveEvent(UUID userId, UUID id) {
        jdbc.sql("""
            INSERT INTO app.saved_event (user_id, event_id)
            SELECT :userId, s.loc_event_id
            FROM publication.discoverable_events s
            WHERE s.loc_group_id = %1$s
              AND NOT EXISTS (SELECT 1 FROM app.saved_event se
                              JOIN publication.event_snapshot g ON g.loc_event_id = se.event_id
                              WHERE se.user_id = :userId AND g.loc_group_id = s.loc_group_id)
            ORDER BY (s.loc_event_id = :id) DESC, %2$s, s.loc_occurrence_id
            LIMIT 1
            ON CONFLICT DO NOTHING
            """.formatted(SHOW_OF, STARTS))
                .param("userId", userId)
                .param("id", id)
                .update();
    }

    /** Removes every saved performance of the show. */
    public void deleteEvent(UUID userId, UUID id) {
        jdbc.sql("""
            DELETE FROM app.saved_event
            WHERE user_id = :userId
              AND (event_id = :id
                   OR event_id IN (SELECT g.loc_event_id FROM publication.event_snapshot g
                                   WHERE g.loc_group_id = %s))
            """.formatted(SHOW_OF))
                .param("userId", userId)
                .param("id", id)
                .update();
    }

    public boolean eventExists(UUID id) {
        return jdbc.sql("""
            SELECT EXISTS (
                SELECT 1
                FROM publication.discoverable_events
                WHERE loc_group_id = %s
            )
            """.formatted(SHOW_OF))
                .param("id", id)
                .query(Boolean.class)
                .single();
    }

    public long countSaved(UUID userId) {
        return jdbc.sql("""
            SELECT COUNT(DISTINCT s.loc_group_id)
            FROM app.saved_event se
            JOIN publication.event_snapshot s ON s.loc_event_id = se.event_id
            WHERE se.user_id = :userId
            """)
                .param("userId", userId)
                .query(Long.class)
                .single();
    }

    /** One row per saved show, saved when its first performance was; shows its next available date, otherwise its latest one. */
    public List<SavedEventResponse> findSaved(UUID userId, int page, int size) {
        String sql = """
            WITH saved AS (
                SELECT g.loc_group_id, min(se.saved_at) AS saved_at
                FROM app.saved_event se
                JOIN (SELECT DISTINCT loc_event_id, loc_group_id FROM publication.event_snapshot) g
                  ON g.loc_event_id = se.event_id
                WHERE se.user_id = :userId
                GROUP BY g.loc_group_id
            )
            SELECT sv.saved_at,
                   s.loc_group_id,
                   s.payload->>'title' AS title,
                   s.payload->'location'->>'type' AS place,
                   %1$s AS start_at,
                   %2$s AS end_at,
                   nullif(trim(s.payload->'location'->>'city'), '') AS city_name,
                   nullif(%3$s, '') AS city_slug,
                   nullif(trim(s.payload->'location'->>'venue_name'), '') AS venue_name,
                   s.payload->'images'->0->>'url' AS image_url,
                   true AS saved,
                   %4$s AS available
            FROM saved sv
            JOIN LATERAL (
                SELECT * FROM publication.event_snapshot s
                WHERE s.loc_group_id = sv.loc_group_id
                ORDER BY %4$s DESC, CASE WHEN %4$s THEN %1$s END, %1$s DESC NULLS LAST, s.loc_occurrence_id
                LIMIT 1
            ) s ON true
            ORDER BY sv.saved_at DESC, sv.loc_group_id
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
