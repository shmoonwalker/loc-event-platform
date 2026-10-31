package nl.loc.backend.user.repository;

import static nl.loc.backend.event.repository.PublicEventRepository.CITY_SLUG;
import static nl.loc.backend.event.repository.PublicEventRepository.ENDS;
import static nl.loc.backend.event.repository.PublicEventRepository.SAVED;
import static nl.loc.backend.event.repository.PublicEventRepository.STARTS;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import nl.loc.backend.event.dto.response.EventCard;
import nl.loc.backend.event.dto.response.OrganizerInfo;
import nl.loc.backend.event.repository.EventCardRowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Follows in {@code app.followed_organizer}; organizer details and events come from the publication snapshots. */
@Repository
public class FollowedOrganizerRepository {

    // upcoming discoverable occurrences with at least one organizer the user follows
    private static final String FOLLOWED_UPCOMING = """
            EXISTS (SELECT 1 FROM app.followed_organizer f
                    WHERE f.user_id = :userId
                      AND s.payload->'organizers' @> jsonb_build_array(
                              jsonb_build_object('locOrganizerId', f.organizer_id::text)))
              AND %1$s IS NOT NULL AND %2$s > :now""".formatted(STARTS, ENDS);

    private final JdbcClient jdbc;
    private final EventCardRowMapper cardMapper;

    public FollowedOrganizerRepository(JdbcClient jdbc, EventCardRowMapper cardMapper) {
        this.jdbc = jdbc;
        this.cardMapper = cardMapper;
    }

    public void follow(UUID userId, UUID organizerId) {
        jdbc.sql("""
            INSERT INTO app.followed_organizer (user_id, organizer_id)
            VALUES (:userId, :organizerId)
            ON CONFLICT DO NOTHING
            """)
                .param("userId", userId)
                .param("organizerId", organizerId)
                .update();
    }

    public void unfollow(UUID userId, UUID organizerId) {
        jdbc.sql("""
            DELETE FROM app.followed_organizer
            WHERE user_id = :userId AND organizer_id = :organizerId
            """)
                .param("userId", userId)
                .param("organizerId", organizerId)
                .update();
    }

    public boolean organizerExists(UUID organizerId) {
        return jdbc.sql("""
            SELECT EXISTS (
                SELECT 1
                FROM publication.organizer_identity WHERE loc_organizer_id = :id
            )
            """)
                .param("id", organizerId)
                .query(Boolean.class)
                .single();
    }

    /** Most recently followed first; details come from the latest snapshot naming the organizer. */
    public List<OrganizerInfo> findFollowed(UUID userId) {
        return jdbc.sql("""
            SELECT f.organizer_id,
                   nullif(trim(o.org->>'name'), '') AS name,
                   nullif(trim(o.org->>'description'), '') AS description,
                   nullif(trim(o.org->>'site'), '') AS site
            FROM app.followed_organizer f
            JOIN LATERAL (
                SELECT org
                FROM publication.event_snapshot s
                CROSS JOIN jsonb_array_elements(s.payload->'organizers') AS org
                WHERE org->>'locOrganizerId' = f.organizer_id::text
                ORDER BY s.updated_at DESC
                LIMIT 1
            ) o ON true
            WHERE f.user_id = :userId
            ORDER BY f.followed_at DESC, f.organizer_id
            """)
                .param("userId", userId)
                .query((rs, row) -> new OrganizerInfo(
                        rs.getObject("organizer_id", UUID.class),
                        rs.getString("name"),
                        rs.getString("description"),
                        rs.getString("site")))
                .list();
    }

    public long countFollowedEvents(UUID userId, Instant now) {
        return jdbc.sql("""
            SELECT count(DISTINCT s.loc_group_id)
            FROM publication.discoverable_events s
            WHERE %s
            """.formatted(FOLLOWED_UPCOMING))
                .param("userId", userId)
                .param("now", OffsetDateTime.ofInstant(now, ZoneOffset.UTC))
                .query(Long.class)
                .single();
    }

    /** Soonest first, one card per show even when several followed organizers run it. */
    public List<EventCard> followedEvents(UUID userId, Instant now, int page, int size) {
        String sql = """
            SELECT * FROM (
                SELECT DISTINCT ON (s.loc_group_id)
                       s.loc_group_id,
                       s.payload->>'title' AS title,
                       s.payload->'location'->>'type' AS place,
                       %1$s AS start_at,
                       %2$s AS end_at,
                       nullif(trim(s.payload->'location'->>'city'), '') AS city_name,
                       nullif(%3$s, '') AS city_slug,
                       nullif(trim(s.payload->'location'->>'venue_name'), '') AS venue_name,
                       s.payload->'images'->0->>'url' AS image_url,
                       %4$s AS saved
                FROM publication.discoverable_events s
                WHERE %5$s
                ORDER BY s.loc_group_id, %1$s, s.loc_occurrence_id
            ) e
            ORDER BY start_at, loc_group_id
            LIMIT :limit OFFSET :offset
            """.formatted(STARTS, ENDS, CITY_SLUG, SAVED, FOLLOWED_UPCOMING);
        return jdbc.sql(sql)
                .param("userId", userId)
                // SAVED checks the viewer, who is the follower here
                .param("viewerId", userId)
                .param("now", OffsetDateTime.ofInstant(now, ZoneOffset.UTC))
                .param("limit", size)
                .param("offset", (long) page * size)
                .query(cardMapper)
                .list();
    }
}
