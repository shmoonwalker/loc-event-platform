package nl.loc.data.catalog;

import java.sql.ResultSet;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Serializes publication and presence changes for a source across app instances. */
@Component
@RequiredArgsConstructor
public class CatalogSourceLock {
    private final JdbcTemplate jdbcTemplate;

    @Transactional(propagation = Propagation.MANDATORY)
    public void acquire(String source) {
        jdbcTemplate.query("SELECT pg_advisory_xact_lock(hashtext(?))",
                (ResultSet result) -> { }, "catalog-source:" + source);
    }
}
