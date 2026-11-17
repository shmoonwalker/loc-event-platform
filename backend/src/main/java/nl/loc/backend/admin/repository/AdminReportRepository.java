package nl.loc.backend.admin.repository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import nl.loc.backend.admin.dto.ReportGroupResponse;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Reads and closes rows in {@code app.report}. Issue 95 owns that table. */
@Repository
public class AdminReportRepository {

    private final JdbcClient jdbc;
    private final JsonMapper json;

    public AdminReportRepository(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /**
     * One group per reported item, with its show and title and, for a comment, its body and author.
     * An event report filed on an older {@code loc_event_id} still resolves to its show.
     * Resolved reports are left out. Newest activity first.
     */
    public List<ReportGroupResponse> listOpen() {
        return jdbc.sql("""
                WITH grouped AS (
                    SELECT target_type,
                           target_id,
                           count(*) AS open_count,
                           to_json(array_agg(DISTINCT reason ORDER BY reason))::text AS reasons,
                           to_json(array_agg(id ORDER BY created_at, id))::text AS report_ids,
                           max(created_at) AS last_reported_at
                    FROM app.report
                    WHERE resolved_at IS NULL
                    GROUP BY target_type, target_id
                )
                SELECT g.*, coalesce(e.event_id, t.id) AS event_id, e.event_title,
                       CASE WHEN c.deleted_at IS NULL THEN c.body END AS comment_body,
                       u.display_name AS comment_author_name
                FROM grouped g
                LEFT JOIN app.comment c ON g.target_type = 'COMMENT' AND c.id = g.target_id
                LEFT JOIN app.app_user u ON u.id = c.user_id
                CROSS JOIN LATERAL (
                    SELECT CASE WHEN g.target_type = 'EVENT' THEN g.target_id ELSE c.event_id END AS id
                ) t
                LEFT JOIN LATERAL (
                    SELECT s.loc_group_id AS event_id, s.payload->>'title' AS event_title
                    FROM publication.event_snapshot s
                    WHERE s.loc_group_id = t.id OR s.loc_event_id = t.id
                    ORDER BY s.updated_at DESC
                    LIMIT 1
                ) e ON true
                ORDER BY g.last_reported_at DESC, g.target_type, g.target_id
                """)
                .query((rs, row) -> new ReportGroupResponse(
                        rs.getString("target_type"),
                        rs.getObject("target_id", UUID.class),
                        rs.getLong("open_count"),
                        strings(rs.getString("reasons")),
                        uuids(rs.getString("report_ids")),
                        rs.getObject("event_id", UUID.class),
                        rs.getString("event_title"),
                        rs.getString("comment_body"),
                        rs.getString("comment_author_name")))
                .list();
    }

    /** Closes one open report. Returns 0 when the id is unknown or already resolved. */
    public int dismiss(UUID reportId, UUID adminId, OffsetDateTime resolvedAt) {
        return jdbc.sql("""
                UPDATE app.report
                SET resolved_at = :resolvedAt, resolved_by = :adminId, resolution = 'DISMISSED'
                WHERE id = :id AND resolved_at IS NULL
                """)
                .param("id", reportId)
                .param("adminId", adminId)
                .param("resolvedAt", resolvedAt)
                .update();
    }

    /** Closes open PHOTO reports of this photo. Event and comment reports stay open. */
    public int resolveOpenPhotoReports(UUID photoId, UUID resolverId, OffsetDateTime resolvedAt) {
        return jdbc.sql("""
                UPDATE app.report
                SET resolved_at = :resolvedAt, resolved_by = :resolverId, resolution = 'REMOVED'
                WHERE target_type = 'PHOTO' AND target_id = :photoId AND resolved_at IS NULL
                """)
                .param("photoId", photoId)
                .param("resolverId", resolverId)
                .param("resolvedAt", resolvedAt)
                .update();
    }

    /** Closes open EVENT reports of this show. Comment reports and other shows stay open. */
    public int resolveOpenEventReports(UUID showId, UUID adminId, OffsetDateTime resolvedAt) {
        return jdbc.sql("""
                UPDATE app.report
                SET resolved_at = :resolvedAt, resolved_by = :adminId, resolution = 'HIDDEN'
                WHERE target_type = 'EVENT' AND target_id = :eventId AND resolved_at IS NULL
                """)
                .param("eventId", showId)
                .param("adminId", adminId)
                .param("resolvedAt", resolvedAt)
                .update();
    }

    private List<String> strings(String jsonText) {
        List<String> values = new ArrayList<>();
        for (JsonNode node : json.readTree(jsonText)) {
            values.add(node.asString());
        }
        return values;
    }

    private List<UUID> uuids(String jsonText) {
        List<UUID> values = new ArrayList<>();
        for (JsonNode node : json.readTree(jsonText)) {
            values.add(UUID.fromString(node.asString()));
        }
        return values;
    }
}
