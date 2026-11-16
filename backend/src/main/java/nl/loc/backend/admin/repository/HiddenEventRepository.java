package nl.loc.backend.admin.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import nl.loc.backend.admin.dto.HiddenEventResponse;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Admin hides in {@code app.hidden_event}. The publication row is never written. */
@Repository
public class HiddenEventRepository {

    private final JdbcClient jdbc;

    public HiddenEventRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Show id for a path id, which may be a show id or an older {@code loc_event_id}. */
    public Optional<UUID> findShowId(UUID id) {
        return jdbc.sql("""
                SELECT g.loc_group_id
                FROM publication.event_snapshot g
                WHERE g.loc_group_id = :id OR g.loc_event_id = :id
                LIMIT 1
                """)
                .param("id", id)
                .query(UUID.class)
                .optional();
    }

    /** Inserts one row. A show that is already hidden is left as it was. */
    public void hide(UUID showId, UUID hiddenBy, String reason, OffsetDateTime hiddenAt) {
        jdbc.sql("""
                INSERT INTO app.hidden_event (event_id, hidden_by, reason, hidden_at)
                VALUES (:eventId, :hiddenBy, :reason, :hiddenAt)
                ON CONFLICT (event_id) DO NOTHING
                """)
                .param("eventId", showId)
                .param("hiddenBy", hiddenBy)
                .param("reason", reason)
                .param("hiddenAt", hiddenAt)
                .update();
    }

    public void unhide(UUID showId) {
        jdbc.sql("DELETE FROM app.hidden_event WHERE event_id = :eventId")
                .param("eventId", showId)
                .update();
    }

    /** Most recently hidden first. */
    public List<HiddenEventResponse> list() {
        return jdbc.sql("""
                SELECT h.event_id, h.hidden_by, u.display_name AS hidden_by_name, h.hidden_at, h.reason
                FROM app.hidden_event h
                JOIN app.app_user u ON u.id = h.hidden_by
                ORDER BY h.hidden_at DESC, h.event_id
                """)
                .query((rs, row) -> new HiddenEventResponse(
                        rs.getObject("event_id", UUID.class),
                        rs.getObject("hidden_by", UUID.class),
                        rs.getString("hidden_by_name"),
                        rs.getObject("hidden_at", OffsetDateTime.class).toInstant(),
                        rs.getString("reason")))
                .list();
    }
}
