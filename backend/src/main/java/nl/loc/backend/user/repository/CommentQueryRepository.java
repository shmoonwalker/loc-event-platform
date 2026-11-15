package nl.loc.backend.user.repository;

import static nl.loc.backend.event.repository.PublicEventRepository.SHOW_OF;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import nl.loc.backend.user.dto.response.CommentAuthor;
import nl.loc.backend.user.dto.response.CommentResponse;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Reads comment threads from {@code app.comment} with their authors from {@code app.app_user}. */
@Repository
public class CommentQueryRepository {

    // the text and author of a deleted comment never leave the database
    private static final String COLUMNS = """
            c.id, c.parent_id, c.official, c.created_at, c.edited_at,
            c.deleted_at IS NOT NULL AS deleted,
            CASE WHEN c.deleted_at IS NULL THEN c.body END AS body,
            CASE WHEN c.deleted_at IS NULL THEN u.display_name END AS display_name,
            COALESCE(c.deleted_at IS NULL AND c.user_id = :viewerId, false) AS mine""";

    // a deleted top-level comment stays only while it has replies, shown as "Comment deleted"
    private static final String VISIBLE_TOP_LEVEL = """
            c.event_id = :eventId AND c.parent_id IS NULL
            AND (c.deleted_at IS NULL OR EXISTS (
                SELECT 1 FROM app.comment r WHERE r.parent_id = c.id AND r.deleted_at IS NULL))""";

    private final JdbcClient jdbc;

    public CommentQueryRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** The show of {@code id} (a show id or an old event id) unless it is hidden; past events stay open. */
    public Optional<UUID> findVisibleShow(UUID id) {
        return jdbc.sql("""
                SELECT s.loc_group_id FROM publication.event_snapshot s
                WHERE s.loc_group_id = %s AND s.state <> 'UNAVAILABLE'
                LIMIT 1
                """.formatted(SHOW_OF))
                .param("id", id)
                .query(UUID.class)
                .optional();
    }

    public long countTopLevel(UUID eventId) {
        return jdbc.sql("SELECT count(*) FROM app.comment c WHERE " + VISIBLE_TOP_LEVEL)
                .param("eventId", eventId)
                .query(Long.class)
                .single();
    }

    public List<CommentResponse> findPage(UUID eventId, UUID viewerId, int page, int size) {
        List<Row> topLevel = jdbc.sql("""
                SELECT %s
                FROM app.comment c LEFT JOIN app.app_user u ON u.id = c.user_id
                WHERE %s
                ORDER BY c.created_at DESC, c.id DESC
                LIMIT :limit OFFSET :offset
                """.formatted(COLUMNS, VISIBLE_TOP_LEVEL))
                .param("eventId", eventId)
                .param("viewerId", viewerId)
                .param("limit", size)
                .param("offset", (long) page * size)
                .query(CommentQueryRepository::row)
                .list();
        if (topLevel.isEmpty()) {
            return List.of();
        }
        // replies can't have replies, so a deleted reply has nothing to keep context for and is left out
        Map<UUID, List<CommentResponse>> replies = jdbc.sql("""
                SELECT %s
                FROM app.comment c LEFT JOIN app.app_user u ON u.id = c.user_id
                WHERE c.parent_id IN (:parentIds) AND c.deleted_at IS NULL
                ORDER BY c.created_at, c.id
                """.formatted(COLUMNS))
                .param("parentIds", topLevel.stream().map(Row::id).toList())
                .param("viewerId", viewerId)
                .query(CommentQueryRepository::row)
                .list().stream()
                .collect(Collectors.groupingBy(Row::parentId,
                        Collectors.mapping(r -> r.toResponse(List.of()), Collectors.toList())));
        List<CommentResponse> items = new ArrayList<>();
        for (Row row : topLevel) {
            items.add(row.toResponse(replies.getOrDefault(row.id(), List.of())));
        }
        return items;
    }

    private static Row row(ResultSet rs, int rowNum) throws SQLException {
        String displayName = rs.getString("display_name");
        OffsetDateTime editedAt = rs.getObject("edited_at", OffsetDateTime.class);
        return new Row(
                rs.getObject("id", UUID.class),
                rs.getObject("parent_id", UUID.class),
                displayName == null ? null : new CommentAuthor(displayName),
                rs.getString("body"),
                rs.getBoolean("official"),
                rs.getBoolean("deleted"),
                rs.getBoolean("mine"),
                rs.getObject("created_at", OffsetDateTime.class),
                editedAt);
    }

    private record Row(UUID id, UUID parentId, CommentAuthor author, String body, boolean official,
                       boolean deleted, boolean mine, OffsetDateTime createdAt, OffsetDateTime editedAt) {

        CommentResponse toResponse(List<CommentResponse> replies) {
            return new CommentResponse(id, author, body, official, deleted, mine, createdAt.toInstant(),
                    editedAt == null ? null : editedAt.toInstant(), replies);
        }
    }
}