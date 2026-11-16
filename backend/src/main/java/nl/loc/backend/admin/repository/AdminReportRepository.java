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

    /** One group per reported item. Resolved reports are left out. Newest activity first. */
    public List<ReportGroupResponse> listOpen() {
        return jdbc.sql("""
                SELECT target_type,
                       target_id,
                       count(*) AS open_count,
                       to_json(array_agg(DISTINCT reason ORDER BY reason))::text AS reasons,
                       to_json(array_agg(id ORDER BY created_at, id))::text AS report_ids
                FROM app.report
                WHERE resolved_at IS NULL
                GROUP BY target_type, target_id
                ORDER BY max(created_at) DESC, target_type, target_id
                """)
                .query((rs, row) -> new ReportGroupResponse(
                        rs.getString("target_type"),
                        rs.getObject("target_id", UUID.class),
                        rs.getLong("open_count"),
                        strings(rs.getString("reasons")),
                        uuids(rs.getString("report_ids"))))
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
