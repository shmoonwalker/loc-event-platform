package nl.loc.data.processing;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** A checkpoint is written in the same transaction as catalog publication. */
@Repository
@RequiredArgsConstructor
public class RawProcessingRepository {
    private final JdbcTemplate jdbcTemplate;

    public boolean isProcessed(String source, String key) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM catalog.processed_raw_object WHERE source = ? AND raw_object_key = ?)",
                Boolean.class, source, key));
    }

    public void markProcessed(String source, String key) {
        jdbcTemplate.update("""
                INSERT INTO catalog.processed_raw_object (source, raw_object_key, processed_at)
                VALUES (?, ?, CURRENT_TIMESTAMP)
                ON CONFLICT (source, raw_object_key) DO UPDATE SET processed_at = EXCLUDED.processed_at
                """, source, key);
    }
}
