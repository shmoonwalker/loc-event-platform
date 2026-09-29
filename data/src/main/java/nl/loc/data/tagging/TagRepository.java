package nl.loc.data.tagging;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class TagRepository {

    private static final RowMapper<TagTarget> TARGET = (ResultSet row, int rowNumber) -> new TagTarget(
            row.getLong("id"),
            row.getString("title"),
            row.getString("description"),
            categories(row.getArray("category_names")),
            row.getInt("attempts"));

    private final JdbcTemplate jdbcTemplate;

    @Transactional(readOnly = true)
    public List<Long> findDueEventIds(Instant now, int promptVersion, int limit) {
        return jdbcTemplate.query("""
                SELECT e.id
                FROM catalog.event e
                         LEFT JOIN catalog.event_tagging t ON t.event_id = e.id
                WHERE e.source_active = TRUE
                  AND e.lifecycle_status <> 'CANCELLED'
                  AND (t.event_id IS NULL
                    OR (t.prompt_version <> ? AND (t.next_check_at IS NULL OR t.next_check_at <= ?))
                    OR (t.gemini_status = 'FAILED' AND (t.next_check_at IS NULL OR t.next_check_at <= ?)))
                ORDER BY e.id
                LIMIT ?
                """, (ResultSet row, int rowNumber) -> row.getLong(1), promptVersion, utc(now), utc(now), limit);
    }

    @Transactional(readOnly = true)
    public TagTarget findActive(long eventId) {
        return jdbcTemplate.query("""
                SELECT e.id,
                       e.title,
                       e.description,
                       COALESCE(ARRAY_AGG(c.name ORDER BY c.name) FILTER (WHERE c.name IS NOT NULL), '{}') AS category_names,
                       COALESCE(t.attempts, 0) AS attempts
                FROM catalog.event e
                         LEFT JOIN catalog.event_category ec ON ec.event_id = e.id
                         LEFT JOIN catalog.category c ON c.id = ec.category_id
                         LEFT JOIN catalog.event_tagging t ON t.event_id = e.id
                WHERE e.id = ?
                  AND e.source_active = TRUE
                  AND e.lifecycle_status <> 'CANCELLED'
                GROUP BY e.id, e.title, e.description, t.attempts
                """, TARGET, eventId).stream().findFirst().orElse(null);
    }

    @Transactional(readOnly = true)
    public boolean alreadyTagged(long eventId, String fingerprint, int promptVersion) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
                SELECT EXISTS (SELECT 1
                               FROM catalog.event_tagging
                               WHERE event_id = ?
                                 AND content_fingerprint = ?
                                 AND prompt_version = ?
                                 AND gemini_status IN ('SUCCEEDED', 'NOT_APPLICABLE'))
                """, Boolean.class, eventId, fingerprint, promptVersion));
    }

    @Transactional(readOnly = true)
    public int geminiAttemptsSince(Instant since) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM catalog.event_tagging
                WHERE last_attempt_at >= ?
                """, Integer.class, utc(since));
        return count == null ? 0 : count;
    }

    @Transactional
    public void claim(long eventId, Instant claimUntil) {
        jdbcTemplate.update("""
                INSERT INTO catalog.event_tagging (event_id, prompt_version, gemini_status, next_check_at)
                VALUES (?, ?, 'FAILED', ?)
                ON CONFLICT (event_id) DO UPDATE SET next_check_at = EXCLUDED.next_check_at
                """, eventId, TagPrompt.VERSION, utc(claimUntil));
    }

    @Transactional
    public void replaceCategoryTags(long eventId, List<ContentTag> tags) {
        jdbcTemplate.update("DELETE FROM catalog.event_tag WHERE event_id = ? AND origin = 'CATEGORY'", eventId);
        insertTags(eventId, tags, "CATEGORY");
    }

    @Transactional
    public void replaceGeminiTags(long eventId, List<ContentTag> tags) {
        jdbcTemplate.update("DELETE FROM catalog.event_tag WHERE event_id = ? AND origin = 'GEMINI'", eventId);
        insertTags(eventId, tags, "GEMINI");
    }

    @Transactional
    public void saveOutcome(long eventId, String fingerprint, String status, Instant taggedAt,
                            Instant nextCheckAt, int attempts, Instant lastAttemptAt, String error) {
        jdbcTemplate.update("""
                INSERT INTO catalog.event_tagging (event_id, content_fingerprint, prompt_version, gemini_status,
                                                   next_check_at, attempts, last_attempt_at, last_error, tagged_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (event_id) DO UPDATE SET content_fingerprint = EXCLUDED.content_fingerprint,
                                                     prompt_version      = EXCLUDED.prompt_version,
                                                     gemini_status       = EXCLUDED.gemini_status,
                                                     next_check_at       = EXCLUDED.next_check_at,
                                                     attempts            = EXCLUDED.attempts,
                                                     last_attempt_at     = EXCLUDED.last_attempt_at,
                                                     last_error          = EXCLUDED.last_error,
                                                     tagged_at           = EXCLUDED.tagged_at
                """, eventId, fingerprint, TagPrompt.VERSION, status, utc(nextCheckAt), attempts,
                utc(lastAttemptAt), error, utc(taggedAt));
    }

    @Transactional
    public void defer(long eventId, Instant nextCheckAt) {
        jdbcTemplate.update("""
                INSERT INTO catalog.event_tagging (event_id, prompt_version, gemini_status, next_check_at)
                VALUES (?, ?, 'FAILED', ?)
                ON CONFLICT (event_id) DO UPDATE SET next_check_at = EXCLUDED.next_check_at
                """, eventId, TagPrompt.VERSION, utc(nextCheckAt));
    }

    /** A new source snapshot must not keep Gemini tags that describe the old text. */
    @Transactional
    public void invalidate(long eventId) {
        jdbcTemplate.update("DELETE FROM catalog.event_tag WHERE event_id = ? AND origin = 'GEMINI'", eventId);
        jdbcTemplate.update("DELETE FROM catalog.event_tagging WHERE event_id = ?", eventId);
    }

    public static String fingerprint(String title, String description) {
        String content = (title == null ? "" : title.strip()) + "\n" + (description == null ? "" : description.strip());
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Could not fingerprint tag inputs", exception);
        }
    }

    private void insertTags(long eventId, List<ContentTag> tags, String origin) {
        for (ContentTag tag : tags) {
            jdbcTemplate.update("""
                    INSERT INTO catalog.event_tag (event_id, tag, origin)
                    VALUES (?, ?, ?)
                    ON CONFLICT (event_id, tag) DO NOTHING
                    """, eventId, tag.slug(), origin);
        }
    }

    private static List<String> categories(java.sql.Array array) throws java.sql.SQLException {
        if (array == null) {
            return List.of();
        }
        Object value = array.getArray();
        if (value instanceof String[] names) {
            return Arrays.asList(names);
        }
        return List.of();
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }
}
