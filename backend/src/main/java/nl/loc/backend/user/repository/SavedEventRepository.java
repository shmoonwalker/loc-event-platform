package nl.loc.backend.user.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class SavedEventRepository {

    private final JdbcClient jdbc;

    public SavedEventRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
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

}
