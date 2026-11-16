package nl.loc.backend.photo.repository;

import static nl.loc.backend.event.repository.PublicEventRepository.SHOW_OF;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import nl.loc.backend.photo.model.PhotoStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Reads event photos with the uploader's display name and the show title. */
@Repository
public class EventPhotoQueryRepository {

    private static final String COLUMNS = """
            p.id, p.event_id, p.status, p.created_at, u.display_name,
            (SELECT s.payload->>'title'
             FROM publication.event_snapshot s
             WHERE s.loc_group_id = p.event_id
             ORDER BY s.updated_at DESC
             LIMIT 1) AS event_title
            """;

    private final JdbcClient jdbc;

    public EventPhotoQueryRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * The show of {@code id} (a show id or an old event id) when a snapshot exists and is not withdrawn.
     * Past, cancelled and postponed shows stay open. A show whose snapshots are all {@code UNAVAILABLE} does not.
     */
    public Optional<UUID> findOpenShow(UUID id) {
        return jdbc.sql("""
                SELECT s.loc_group_id FROM publication.event_snapshot s
                WHERE s.loc_group_id = %s AND s.state <> 'UNAVAILABLE'
                LIMIT 1
                """.formatted(SHOW_OF))
                .param("id", id)
                .query(UUID.class)
                .optional();
    }

    public List<PhotoView> approved(UUID eventId) {
        return list("""
                SELECT %s
                FROM app.event_photo p
                JOIN app.app_user u ON u.id = p.uploader_id
                WHERE p.event_id = :eventId AND p.status = 'APPROVED'
                ORDER BY p.created_at DESC, p.id DESC
                """.formatted(COLUMNS), eventId, null, null);
    }

    public List<PhotoView> own(UUID eventId, UUID uploaderId) {
        return list("""
                SELECT %s
                FROM app.event_photo p
                JOIN app.app_user u ON u.id = p.uploader_id
                WHERE p.event_id = :eventId AND p.uploader_id = :uploaderId
                ORDER BY p.created_at DESC, p.id DESC
                """.formatted(COLUMNS), eventId, uploaderId, null);
    }

    public List<PhotoView> byStatus(PhotoStatus status, int limit) {
        return list("""
                SELECT %s
                FROM app.event_photo p
                JOIN app.app_user u ON u.id = p.uploader_id
                WHERE p.status = :status
                ORDER BY p.created_at DESC, p.id DESC
                LIMIT :limit
                """.formatted(COLUMNS), null, null, status, limit);
    }

    public String title(UUID showId) {
        return jdbc.sql("""
                SELECT s.payload->>'title'
                FROM publication.event_snapshot s
                WHERE s.loc_group_id = :showId
                ORDER BY s.updated_at DESC
                LIMIT 1
                """)
                .param("showId", showId)
                .query(String.class)
                .optional()
                .orElse(null);
    }

    public Optional<PhotoView> find(UUID photoId) {
        return jdbc.sql("""
                SELECT %s
                FROM app.event_photo p
                JOIN app.app_user u ON u.id = p.uploader_id
                WHERE p.id = :id
                """.formatted(COLUMNS))
                .param("id", photoId)
                .query(this::view)
                .optional();
    }

    private List<PhotoView> list(String sql, UUID eventId, UUID uploaderId, PhotoStatus status) {
        return list(sql, eventId, uploaderId, status, 0);
    }

    private List<PhotoView> list(String sql, UUID eventId, UUID uploaderId, PhotoStatus status, int limit) {
        var query = jdbc.sql(sql);
        if (eventId != null) {
            query = query.param("eventId", eventId);
        }
        if (uploaderId != null) {
            query = query.param("uploaderId", uploaderId);
        }
        if (status != null) {
            query = query.param("status", status.name());
        }
        if (limit > 0) {
            query = query.param("limit", limit);
        }
        return query.query(this::view).list();
    }

    private PhotoView view(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        OffsetDateTime createdAt = rs.getObject("created_at", OffsetDateTime.class);
        return new PhotoView(
                rs.getObject("id", UUID.class),
                rs.getObject("event_id", UUID.class),
                rs.getString("event_title"),
                PhotoStatus.valueOf(rs.getString("status")),
                createdAt.toInstant(),
                rs.getString("display_name"));
    }

    public record PhotoView(UUID id, UUID eventId, String eventTitle, PhotoStatus status,
                            java.time.Instant createdAt, String displayName) {
    }
}
