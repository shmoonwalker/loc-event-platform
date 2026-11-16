package nl.loc.backend.user.repository;

import static nl.loc.backend.event.repository.PublicEventRepository.SHOW_OF;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Resolves the show a member is reporting. A hidden event stays reportable. */
@Repository
public class ReportQueryRepository {

    private final JdbcClient jdbc;

    public ReportQueryRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** The show of {@code id} (a show id or an old event id) when any snapshot of it exists. */
    public Optional<UUID> findShow(UUID id) {
        return jdbc.sql("""
                SELECT s.loc_group_id
                FROM publication.event_snapshot s
                WHERE s.loc_group_id = %s
                LIMIT 1
                """.formatted(SHOW_OF))
                .param("id", id)
                .query(UUID.class)
                .optional();
    }
}
