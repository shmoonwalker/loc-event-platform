package nl.loc.data.publication;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import nl.loc.data.event.EventTimeSlot;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Recover identity when catalogue rows are rebuilt but the durable publication registry survives. */
@Component
@RequiredArgsConstructor
public class OccurrenceIdentityRegistry {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper();

    public record Restoration(UUID id, boolean ambiguous) { }

    public Restoration restore(String source, String externalId, EventTimeSlot incoming, boolean singleSlot) {
        List<Identity> candidates = jdbc.query("""
                SELECT o.loc_occurrence_id, o.schedule::text, o.retired
                FROM publication.occurrence_identity o JOIN publication.event_identity e USING(loc_event_id)
                WHERE e.source=? AND e.external_id=?
                """, (rs, row) -> new Identity(rs.getObject(1, UUID.class), parse(rs.getString(2)), rs.getBoolean(3)), source, externalId);
        List<Identity> exact = candidates.stream().filter(row -> sameStart(row.schedule(), incoming)).toList();
        if (exact.size() == 1) return new Restoration(exact.getFirst().id(), false);
        if (exact.size() > 1) return new Restoration(null, true);
        List<Identity> active = candidates.stream().filter(row -> !row.retired()).toList();
        if (singleSlot && "ticketmaster".equals(source) && active.size() == 1) return new Restoration(active.getFirst().id(), false);
        return new Restoration(null, false);
    }

    private static boolean sameStart(JsonNode schedule, EventTimeSlot slot) {
        var start = PublicationPolicy.instant(schedule, "starts_at");
        if (start != null && slot.startsAt() != null) return start.equals(slot.startsAt());
        return slot.localStartDate() != null && slot.localStartDate().toString().equals(PublicationPolicy.text(schedule, "local_start_date"))
                && java.util.Objects.equals(slot.localStartTime() == null ? null : slot.localStartTime().toString(),
                    normalizedTime(schedule))
                && java.util.Objects.equals(slot.timezone(), PublicationPolicy.text(schedule, "timezone"));
    }

    private static String normalizedTime(JsonNode node) {
        String value = PublicationPolicy.text(node, "local_start_time");
        return value == null ? null : java.time.LocalTime.parse(value).toString();
    }
    private JsonNode parse(String value) {
        try { return json.readTree(value); }
        catch (Exception exception) { throw new IllegalStateException("Invalid identity schedule", exception); }
    }
    private record Identity(UUID id, JsonNode schedule, boolean retired) { }
}
