package nl.loc.data.publication;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import lombok.RequiredArgsConstructor;
import nl.loc.data.catalog.CatalogSourceLock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The shared-database publication boundary. No dependency on the legacy backend schema. */
@Service
@RequiredArgsConstructor
public class PublicationService {
    /** Freshness and presence can clear on a later collection without a content-hash change. */
    private static final Set<String> PRESENCE_REASONS = Set.of(
            "STALE_DETAILS", "STALE_SOURCE_OBSERVATION", "SOURCE_ABSENT", "SOURCE_NOT_VERIFIED",
            "DETAILS_NOT_VERIFIED", "INVALID_OBSERVATION_TIME", "INVALID_COLLECTION_TIME");
    private static final Duration PRESENCE_RETRY = Duration.ofHours(6);
    private static final Duration STRUCTURAL_RETRY = Duration.ofHours(24);
    /** Forecasts refresh every one to three days, so this hides only a forecast whose refreshes stopped. */
    private static final Duration FORECAST_MAX_AGE = Duration.ofDays(4);

    private final JdbcTemplate jdbc;
    private final PublicationPolicy policy;
    private final CatalogSourceLock sourceLock;
    private final ObjectMapper json = new ObjectMapper();

    /**
     * One publication answer for enrichment. {@code retryAt == null} means an occurrence is
     * eligible now, so an API call is allowed. Otherwise wait until {@code retryAt}.
     */
    public record Readiness(Instant retryAt, List<String> reasons) {
        public boolean eligible() { return retryAt == null; }
    }

    /** True when missing Gemini tags are the only thing keeping an active occurrence out of the product. */
    @Transactional(readOnly = true)
    public boolean readyForTagging(long eventId) {
        JsonNode input = load(eventId);
        if (input == null) return false;
        Instant now = Instant.now();
        for (JsonNode slot : input.path("slots")) {
            if (slot.path("retired").asBoolean()) continue;
            if (policy.evaluate(input, slot, now).reasons().equals(List.of("AWAITING_TAGS"))) return true;
        }
        return false;
    }

    /** True when this occurrence itself would be published. */
    @Transactional(readOnly = true)
    public Readiness slotReadiness(long timeSlotId) {
        Instant now = Instant.now();
        List<Long> eventIds = jdbc.query("SELECT event_id FROM catalog.event_time_slot WHERE id = ?",
                (rs, row) -> rs.getLong(1), timeSlotId);
        if (eventIds.isEmpty()) return held(now, false, null, List.of("MISSING_SLOT"));
        JsonNode input = load(eventIds.getFirst());
        if (input == null) return held(now, false, null, List.of("MISSING_EVENT"));
        JsonNode slot = null;
        for (JsonNode candidate : input.path("slots")) {
            if (candidate.path("id").asLong() == timeSlotId) slot = candidate;
        }
        if (slot == null || slot.path("retired").asBoolean()) return held(now, false, null, List.of("OCCURRENCE_REMOVED"));
        return decide(policy.evaluate(input, slot, now), now);
    }

    private static Readiness decide(PublicationPolicy.Decision result, Instant now) {
        if (result.eligible()) return new Readiness(null, List.of());
        return held(now, PRESENCE_REASONS.containsAll(result.reasons()), result.nextCheck(), result.reasons());
    }

    private static Readiness held(Instant now, boolean presenceOnly, Instant presenceNext, List<String> reasons) {
        Instant floor = now.plus(presenceOnly ? PRESENCE_RETRY : STRUCTURAL_RETRY);
        Instant retry = presenceOnly && presenceNext != null && presenceNext.isAfter(floor) ? presenceNext : floor;
        return new Readiness(retry, reasons);
    }

    public List<Long> page(long after, int limit) {
        return jdbc.query("SELECT id FROM catalog.event WHERE id > ? ORDER BY id LIMIT ?",
                (rs, row) -> rs.getLong(1), after, limit);
    }

    @Transactional
    public void evaluate(long eventId) {
        // Serialize all log writers: sequence order is also commit order. Consumers can use
        // sequence > cursor without skipping a transaction that commits late.
        jdbc.query("SELECT pg_advisory_xact_lock(hashtext('loc-final-publication'))", (ResultSet rs) -> { });
        List<String> sources = jdbc.query("SELECT source FROM catalog.event WHERE id = ?",
                (rs, row) -> rs.getString(1), eventId);
        if (sources.isEmpty()) return;
        sourceLock.acquire(sources.getFirst());
        JsonNode input = load(eventId);
        if (input == null) return;
        Instant now = Instant.now();
        JsonNode event = input.path("event");
        String source = event.path("source").asText(), externalId = event.path("external_id").asText();
        jdbc.update("INSERT INTO publication.event_identity(source, external_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
                source, externalId);
        UUID publicEvent = jdbc.queryForObject("SELECT loc_event_id FROM publication.event_identity WHERE source=? AND external_id=?",
                UUID.class, source, externalId);
        String revision = hash(input.toString() + policy.version());
        List<Boolean> unchanged = jdbc.query("""
                SELECT input_revision = ? AND next_evaluation_at > ? FROM publication.decision WHERE subject_id = ?
                """, (rs, row) -> rs.getBoolean(1), revision, utc(now), publicEvent);
        if (!unchanged.isEmpty() && unchanged.getFirst()) return;
        List<JsonNode> slots = new ArrayList<>();
        input.path("slots").forEach(slots::add);
        boolean anyEligible = false;
        Set<String> eventReasons = new TreeSet<>(), eventWarnings = new TreeSet<>();
        Instant next = null;
        for (JsonNode slot : slots) {
            UUID occurrence = UUID.fromString(slot.path("occurrence_key").asText());
            jdbc.update("""
                    INSERT INTO publication.occurrence_identity(loc_occurrence_id, loc_event_id, schedule, retired)
                    VALUES (?, ?, CAST(? AS jsonb), ?) ON CONFLICT(loc_occurrence_id) DO UPDATE
                    SET schedule=EXCLUDED.schedule, retired=EXCLUDED.retired
                    """, occurrence, publicEvent, slot.toString(), slot.path("retired").asBoolean());
            PublicationPolicy.Decision result = policy.evaluate(input, slot, now);
            if (!slot.path("retired").asBoolean()) {
                anyEligible |= result.eligible();
                eventReasons.addAll(result.reasons());
                eventWarnings.addAll(result.warnings());
                next = next == null || result.nextCheck().isBefore(next) ? result.nextCheck() : next;
            }
            saveDecision(occurrence, publicEvent, "OCCURRENCE", result, now, revision);
            publish(input, slot, publicEvent, occurrence, result, now);
        }
        // A catalogue rebuild can omit an occurrence that already had a public snapshot.
        // Explicitly retire/withdraw it; omission alone must never leave it discoverable.
        Set<String> currentIds = new HashSet<>();
        slots.forEach(slot -> currentIds.add(slot.path("occurrence_key").asText()));
        List<String> known = jdbc.query("SELECT schedule::text FROM publication.occurrence_identity WHERE loc_event_id=?",
                (rs, row) -> rs.getString(1), publicEvent);
        for (String saved : known) {
            ObjectNode retired = (ObjectNode) read(saved);
            String key = retired.path("occurrence_key").asText();
            if (key.isBlank() || currentIds.contains(key)) continue;
            retired.put("retired", true);
            UUID occurrence = UUID.fromString(key);
            jdbc.update("UPDATE publication.occurrence_identity SET retired=TRUE WHERE loc_occurrence_id=?", occurrence);
            PublicationPolicy.Decision removed = policy.evaluate(input, retired, now);
            saveDecision(occurrence, publicEvent, "OCCURRENCE", removed, now, revision);
            publish(input, retired, publicEvent, occurrence, removed, now);
        }
        if (next == null) {
            PublicationPolicy.Decision missing = policy.evaluate(input, null, now);
            eventReasons.addAll(missing.reasons());
            eventWarnings.addAll(missing.warnings());
            next = missing.nextCheck();
        }
        if (anyEligible && !eventReasons.isEmpty()) eventWarnings.add("SOME_OCCURRENCES_HELD");
        saveDecision(publicEvent, publicEvent, "EVENT", new PublicationPolicy.Decision(
                anyEligible ? List.of() : List.copyOf(eventReasons), List.copyOf(eventWarnings), next, null,
                anyEligible ? "PUBLISHED" : "UNAVAILABLE"), now, revision);
    }

    private JsonNode load(long id) {
        List<String> rows = jdbc.query("""
                SELECT jsonb_build_object(
                    'event', to_jsonb(e),
                    'location', CASE WHEN o.source_location_fingerprint = md5(COALESCE((to_jsonb(l)-'event_id')::text,'null'))
                                     THEN to_jsonb(o) ELSE to_jsonb(l) END,
                    'location_override_stale', o.source IS NOT NULL AND
                        o.source_location_fingerprint <> md5(COALESCE((to_jsonb(l)-'event_id')::text,'null')),
                    'review', to_jsonb(r),
                    'slots', COALESCE((SELECT jsonb_agg(to_jsonb(s) ORDER BY s.id)
                                      FROM catalog.event_time_slot s WHERE s.event_id=e.id), '[]'::jsonb),
                    'categories', COALESCE((SELECT jsonb_agg(c.name ORDER BY c.name)
                                           FROM catalog.event_category ec JOIN catalog.category c ON c.id=ec.category_id
                                           WHERE ec.event_id=e.id), '[]'::jsonb),
                    'images', COALESCE((SELECT jsonb_agg(to_jsonb(i) ORDER BY i.display_order)
                                       FROM catalog.event_image i WHERE i.event_id=e.id), '[]'::jsonb),
                    'organizers', COALESCE((SELECT jsonb_agg(to_jsonb(org) ORDER BY org.organizer_index)
                                           FROM catalog.event_organizer org WHERE org.event_id=e.id), '[]'::jsonb),
                    'tags', COALESCE((SELECT jsonb_agg(to_jsonb(t) ORDER BY t.tag) FROM catalog.event_tag t
                                     WHERE t.event_id=e.id), '[]'::jsonb),
                    'tagging', (SELECT jsonb_build_object('gemini_status', t.gemini_status)
                                FROM catalog.event_tagging t WHERE t.event_id=e.id),
                    'weather', COALESCE((SELECT jsonb_agg(to_jsonb(w) - 'next_check_at' - 'last_attempt_at'
                                                          - 'attempts' - 'last_error' ORDER BY w.time_slot_id)
                                        FROM catalog.event_weather w JOIN catalog.event_time_slot s ON s.id=w.time_slot_id
                                        WHERE s.event_id=e.id AND NOT s.retired), '[]'::jsonb)
                )::text
                FROM catalog.event e LEFT JOIN catalog.event_location l ON l.event_id=e.id
                LEFT JOIN publication.location_override o ON o.source=e.source AND o.external_id=e.external_id
                LEFT JOIN publication.event_review r ON r.source=e.source AND r.external_id=e.external_id
                WHERE e.id=?
                """, (rs, row) -> rs.getString(1), id);
        return rows.isEmpty() ? null : read(rows.getFirst());
    }

    private void saveDecision(UUID subject, UUID event, String type, PublicationPolicy.Decision result,
                              Instant now, String inputRevision) {
        String reasons = json.valueToTree(result.reasons()).toString();
        String warnings = json.valueToTree(result.warnings()).toString();
        String decision = result.eligible() ? "ELIGIBLE" : "HELD";
        List<String> previous = jdbc.query("""
                SELECT jsonb_build_object('decision',decision,'reasons',blocking_reasons,'warnings',warnings,
                                          'ruleVersion',rule_version,'inputRevision',input_revision)::text
                FROM publication.decision WHERE subject_id=?
                """, (rs, row) -> rs.getString(1), subject);
        ObjectNode audit = json.createObjectNode().put("decision", decision).put("ruleVersion", policy.version())
                .put("inputRevision", inputRevision);
        audit.set("reasons", read(reasons)); audit.set("warnings", read(warnings));
        if (previous.isEmpty() || !read(previous.getFirst()).equals(audit)) {
            ObjectNode history = audit.deepCopy();
            history.put("evaluatedAt", now.toString());
            jdbc.update("INSERT INTO publication.decision_history(subject_id,decision) VALUES (?,CAST(? AS jsonb))",
                    subject, history.toString());
        }
        jdbc.update("""
                INSERT INTO publication.decision(subject_id,loc_event_id,subject_type,decision,blocking_reasons,
                    warnings,evaluated_at,rule_version,input_revision,next_evaluation_at)
                VALUES (?,?,?,?,CAST(? AS jsonb),CAST(? AS jsonb),?,?,?,?)
                ON CONFLICT(subject_id) DO UPDATE SET decision=EXCLUDED.decision,
                    blocking_reasons=EXCLUDED.blocking_reasons,warnings=EXCLUDED.warnings,
                    evaluated_at=EXCLUDED.evaluated_at,rule_version=EXCLUDED.rule_version,
                    input_revision=EXCLUDED.input_revision,next_evaluation_at=EXCLUDED.next_evaluation_at
                """, subject, event, type, decision, reasons, warnings, utc(now), policy.version(), inputRevision, utc(result.nextCheck()));
    }

    private void publish(JsonNode input, JsonNode slot, UUID eventId, UUID occurrence,
                         PublicationPolicy.Decision result, Instant now) {
        List<Stored> existing = jdbc.query("""
                SELECT revision, payload::text, discoverable, state FROM publication.event_snapshot WHERE loc_occurrence_id=?
                """, (rs, row) -> new Stored(rs.getLong(1), read(rs.getString(2)), rs.getBoolean(3), rs.getString(4)), occurrence);
        // Never-qualified records have decisions, but no publicly readable detail snapshot.
        if (!result.eligible() && existing.isEmpty()) return;
        ObjectNode payload;
        if (result.eligible()) payload = payload(input, slot, eventId, occurrence, result);
        else {
            payload = (ObjectNode) existing.getFirst().payload().deepCopy();
            payload.put("state", result.state());
            payload.put("discoverable", false);
            payload.put("detailsCurrent", false);
            payload.remove("weather");
            payload.remove("duplicateOf");
            payload.set("lifecycle", input.path("event").path("lifecycle_status"));
            // Never continue linking an invalid source destination from an older snapshot.
            String currentUrl = PublicationPolicy.text(input.path("event"), "source_url");
            payload.put("sourceUrl", PublicationPolicy.publicUrl(currentUrl) ? currentUrl : null);
            if (result.reasons().contains("VERIFIED_DUPLICATE")) payload.set("duplicateOf", input.path("review").path("duplicate_of"));
        }
        if (!existing.isEmpty() && existing.getFirst().payload().equals(payload)
                && existing.getFirst().discoverable() == result.eligible()
                && existing.getFirst().state().equals(result.state())) return;
        long revision = existing.isEmpty() ? 1 : existing.getFirst().revision() + 1;
        jdbc.update("""
                INSERT INTO publication.event_snapshot(loc_occurrence_id,loc_event_id,revision,discoverable,state,
                    payload,first_published_at,updated_at) VALUES (?,?,?,?,?,CAST(? AS jsonb),?,?)
                ON CONFLICT(loc_occurrence_id) DO UPDATE SET revision=EXCLUDED.revision,
                    discoverable=EXCLUDED.discoverable,state=EXCLUDED.state,payload=EXCLUDED.payload,updated_at=EXCLUDED.updated_at
                """, occurrence, eventId, revision, result.eligible(), result.state(), payload.toString(), utc(now), utc(now));
        ObjectNode envelope = json.createObjectNode().put("schemaVersion", 1).put("locEventId", eventId.toString())
                .put("locOccurrenceId", occurrence.toString()).put("revision", revision)
                .put("operation", result.eligible() ? "UPSERT" : "WITHDRAW").put("updatedAt", now.toString());
        envelope.set("event", payload);
        jdbc.update("""
                INSERT INTO publication.change_log(loc_occurrence_id,revision,operation,payload,created_at)
                VALUES (?,?,?,CAST(? AS jsonb),?)
                """, occurrence, revision, result.eligible() ? "UPSERT" : "WITHDRAW", envelope.toString(), utc(now));
    }

    private ObjectNode payload(JsonNode input, JsonNode slot, UUID eventId, UUID occurrence,
                               PublicationPolicy.Decision result) {
        JsonNode event = input.path("event"), location = input.path("location");
        String source = PublicationPolicy.text(event, "source");
        ObjectNode output = json.createObjectNode().put("schemaVersion", 1)
                .put("locEventId", eventId.toString()).put("locOccurrenceId", occurrence.toString())
                .put("discoverable", true).put("detailsCurrent", true).put("state", result.state());
        for (String key : List.of("title", "description")) output.put(key,
                nl.loc.data.text.HtmlPlainText.convert(PublicationPolicy.text(event, key)));
        output.set("source", event.path("source"));
        output.set("sourceUrl", event.path("source_url"));
        output.set("lifecycle", event.path("lifecycle_status"));
        output.set("organizers", organizers(source, input.path("organizers")));
        output.set("categories", input.path("categories").isEmpty() ? json.valueToTree(List.of("Other")) : input.path("categories"));
        Set<String> tags = new TreeSet<>();
        boolean geminiSucceeded = "SUCCEEDED".equals(input.path("tagging").path("gemini_status").asText());
        for (JsonNode tag : input.path("tags")) {
            String origin = tag.path("origin").asText();
            if ("CATEGORY".equals(origin) || ("GEMINI".equals(origin) && geminiSucceeded)) tags.add(tag.path("tag").asText());
        }
        String other = nl.loc.data.tagging.ContentTag.OTHER.slug();
        if (tags.size() > 1) tags.remove(other);
        if (tags.isEmpty()) tags.add(other);
        output.set("tags", json.valueToTree(tags));
        output.set("sourceVerifiedAt", event.path("collected_at"));
        ObjectNode schedule = output.putObject("schedule");
        for (String key : List.of("starts_at", "ends_at", "local_start_date", "local_start_time", "local_end_date",
                "local_end_time", "timezone", "start_date_status", "start_time_status", "end_date_status",
                "end_time_status", "end_approximate")) schedule.set(key, slot.path(key));
        output.put("discoveryUntil", result.discoveryUntil() == null ? null : result.discoveryUntil().toString());
        output.put("validUntil", policy.validUntil(event, result.discoveryUntil()).toString());
        ObjectNode place = output.putObject("location");
        place.set("type", location.path("location_type"));
        if ("PHYSICAL".equals(location.path("location_type").asText())) {
            for (String key : List.of("venue_name", "address", "city", "postal_code", "country", "country_code", "latitude", "longitude")) {
                place.set(key, location.path(key));
            }
        }
        var images = output.putArray("images");
        for (JsonNode image : input.path("images")) {
            if (!PublicationPolicy.publicUrl(PublicationPolicy.text(image, "url"))) continue;
            ObjectNode publicImage = json.createObjectNode();
            for (String key : List.of("url", "width", "height", "ratio", "attribution", "fallback")) publicImage.set(key, image.path(key));
            images.add(publicImage);
        }
        if ("PHYSICAL".equals(location.path("location_type").asText())) {
            for (JsonNode weather : input.path("weather")) {
                Instant fetched = PublicationPolicy.instant(weather, "forecast_fetched_at");
                if (weather.path("time_slot_id").asLong() != slot.path("id").asLong() || fetched == null
                        || fetched.isBefore(Instant.now().minus(FORECAST_MAX_AGE))) continue;
                Instant start = PublicationPolicy.instant(slot, "starts_at");
                Instant requested = PublicationPolicy.instant(weather, "requested_for_hour");
                if (start == null || requested == null || !requested.equals(start.truncatedTo(java.time.temporal.ChronoUnit.HOURS))
                        || weather.path("requested_latitude").asDouble() != location.path("latitude").asDouble()
                        || weather.path("requested_longitude").asDouble() != location.path("longitude").asDouble()) continue;
                ObjectNode forecast = output.putObject("weather");
                for (String key : List.of("forecast_fetched_at", "requested_for_hour", "temperature_celsius",
                        "precipitation_probability_percent", "wind_speed_kmh", "weather_code")) forecast.set(key, weather.path(key));
            }
        }
        return output;
    }

    private JsonNode organizers(String source, JsonNode organizers) {
        var published = json.createArrayNode();
        if (source == null || !organizers.isArray()) return published;
        for (JsonNode organizer : organizers) {
            String externalId = PublicationPolicy.text(organizer, "external_id");
            String name = nl.loc.data.text.HtmlPlainText.convert(PublicationPolicy.text(organizer, "name"));
            if (externalId == null || name == null) continue;
            UUID id = organizerId(source, externalId);
            ObjectNode record = json.createObjectNode()
                    .put("locOrganizerId", id.toString())
                    .put("name", name);
            String description = nl.loc.data.text.HtmlPlainText.convert(PublicationPolicy.text(organizer, "description"));
            if (description != null) record.put("description", description);
            String site = PublicationPolicy.text(organizer, "site");
            if (site != null) record.put("site", site);
            published.add(record);
        }
        return published;
    }

    private UUID organizerId(String source, String externalId) {
        jdbc.update("INSERT INTO publication.organizer_identity(source, external_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
                source, externalId);
        return jdbc.queryForObject(
                "SELECT loc_organizer_id FROM publication.organizer_identity WHERE source=? AND external_id=?",
                UUID.class, source, externalId);
    }

    private JsonNode read(String value) {
        try { return json.readTree(value); }
        catch (Exception exception) { throw new IllegalStateException("Cannot read publication JSON", exception); }
    }
    private static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception exception) { throw new IllegalStateException(exception); }
    }
    private static OffsetDateTime utc(Instant value) { return value.atOffset(ZoneOffset.UTC); }
    private record Stored(long revision, JsonNode payload, boolean discoverable, String state) { }
}
