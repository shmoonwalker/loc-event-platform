package nl.loc.data.publication;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Pure qualification rules. Catalogue ingestion deliberately does not use this gate. */
@Component
public class PublicationPolicy {
    private final Set<String> countries;
    private final Duration maxAge;
    private final Duration rvoMaxAge;
    private final Duration ticketmasterMaxAge;
    private final Duration checkInterval;
    private final boolean includeSeasonPasses;

    public PublicationPolicy(@Value("${loc.publication.countries:NL}") String countries,
                             @Value("${loc.publication.max-detail-age:PT72H}") Duration maxAge,
                             @Value("${loc.publication.rvo-max-detail-age:${loc.publication.max-detail-age:PT72H}}") Duration rvoMaxAge,
                             @Value("${loc.publication.ticketmaster-max-detail-age:${loc.publication.max-detail-age:PT72H}}") Duration ticketmasterMaxAge,
                             @Value("${loc.publication.recheck-interval:PT5M}") Duration checkInterval,
                             @Value("${loc.publication.include-season-passes:false}") boolean includeSeasonPasses) {
        this.countries = new TreeSet<>();
        Arrays.stream(countries.split(",")).map(String::strip).filter(s -> !s.isEmpty())
                .map(s -> s.toUpperCase(Locale.ROOT)).forEach(this.countries::add);
        if (this.countries.isEmpty() || maxAge.isNegative() || maxAge.isZero()
                || rvoMaxAge.isNegative() || rvoMaxAge.isZero() || ticketmasterMaxAge.isNegative() || ticketmasterMaxAge.isZero()
                || checkInterval.isNegative() || checkInterval.isZero()) {
            throw new IllegalArgumentException("Publication countries and positive freshness/recheck durations required");
        }
        this.maxAge = maxAge;
        this.rvoMaxAge = rvoMaxAge;
        this.ticketmasterMaxAge = ticketmasterMaxAge;
        this.checkInterval = checkInterval;
        this.includeSeasonPasses = includeSeasonPasses;
    }

    public String version() { return "loc-v2:" + countries + ":" + maxAge + ":rvo=" + rvoMaxAge
            + ":ticketmaster=" + ticketmasterMaxAge + ":" + checkInterval + ":passes=" + includeSeasonPasses; }

    private Duration maxAge(JsonNode event) {
        return switch (event.path("source").asText()) {
            case "rvo" -> rvoMaxAge;
            case "ticketmaster" -> ticketmasterMaxAge;
            default -> maxAge;
        };
    }

    public Instant validUntil(JsonNode event, Instant discoveryUntil) {
        Duration maxAge = maxAge(event);
        Instant expiry = earlier(instant(event, "collected_at").plus(maxAge), instant(event, "last_seen_at").plus(maxAge));
        return discoveryUntil == null ? expiry : earlier(expiry, discoveryUntil);
    }

    public record Decision(List<String> reasons, List<String> warnings, Instant nextCheck,
                           Instant discoveryUntil, String state) {
        public boolean eligible() { return reasons.isEmpty(); }
    }

    public Decision evaluate(JsonNode input, JsonNode slot, Instant now) {
        Set<String> reasons = new TreeSet<>();
        Set<String> warnings = new TreeSet<>();
        JsonNode event = input.path("event");
        Duration maxAge = maxAge(event);
        JsonNode location = input.path("location");
        for (JsonNode issue : event.path("qualification_issues")) {
            if ("AMBIGUOUS_VENUE".equals(issue.asText())
                    && location.hasNonNull("evidence_url") && publicUrl(text(location, "evidence_url"))) continue;
            if (!includeSeasonPasses || !"SEASON_PASS".equals(issue.asText())) reasons.add(issue.asText());
        }
        if (input.path("location_override_stale").asBoolean()) warnings.add("LOCATION_OVERRIDE_STALE");
        if (location.hasNonNull("evidence_url") && !publicUrl(text(location, "evidence_url"))) reasons.add("INVALID_LOCATION_EVIDENCE");
        if (!input.path("review").isNull() && input.path("review").hasNonNull("duplicate_of")) reasons.add("VERIFIED_DUPLICATE");
        if (input.path("review").path("blocked").asBoolean()) reasons.add("EDITORIAL_HOLD");
        String title = nl.loc.data.text.HtmlPlainText.convert(text(event, "title"));
        if (!includeSeasonPasses && title != null && title.toLowerCase(Locale.ROOT)
                .matches(".*\\b(seizoenskaart|seizoenkaart|season pass|season ticket)\\b.*")) reasons.add("SEASON_PASS");
        if (title == null || !title.codePoints().anyMatch(Character::isLetterOrDigit)
                || Set.of("test", "tba", "tbd", "untitled", "placeholder").contains(title.toLowerCase(Locale.ROOT))) {
            reasons.add("INVALID_TITLE");
        }
        if (!publicUrl(text(event, "source_url"))) reasons.add("INVALID_SOURCE_URL");
        String description = nl.loc.data.text.HtmlPlainText.convert(text(event, "description"));
        if (description == null) warnings.add("MISSING_DESCRIPTION");
        // An event with a real description is published once Gemini has tagged it; others use local tags.
        String taggingStatus = input.path("tagging").path("gemini_status").asText("");
        if (nl.loc.data.tagging.TagTarget.needsGemini(description)
                && !Set.of("SUCCEEDED", "GAVE_UP").contains(taggingStatus)) reasons.add("AWAITING_TAGS");
        if (input.path("images").isEmpty()) warnings.add("MISSING_IMAGE");
        if (input.path("categories").isEmpty()
                || (input.path("categories").size() == 1 && "Other".equals(input.path("categories").get(0).asText()))) warnings.add("CATEGORY_OTHER");
        if (!event.path("source_active").asBoolean()) reasons.add("SOURCE_ABSENT");
        Instant lastSeen = instant(event, "last_seen_at");
        if (lastSeen == null) reasons.add("SOURCE_NOT_VERIFIED");
        Instant collected = instant(event, "collected_at");
        Instant next = now.plus(checkInterval);
        if (lastSeen != null) {
            if (lastSeen.isAfter(now.plusSeconds(300))) reasons.add("INVALID_OBSERVATION_TIME");
            else if (!lastSeen.plus(maxAge).isAfter(now)) reasons.add("STALE_SOURCE_OBSERVATION");
            else next = earlier(next, lastSeen.plus(maxAge));
        }
        if (collected == null) reasons.add("DETAILS_NOT_VERIFIED");
        else if (collected.isAfter(now.plusSeconds(300))) reasons.add("INVALID_COLLECTION_TIME");
        else {
            Instant expiry = collected.plus(maxAge);
            if (!expiry.isAfter(now)) reasons.add("STALE_DETAILS");
            else next = earlier(next, expiry);
        }
        String lifecycle = event.path("lifecycle_status").asText("UNKNOWN");
        if ("CANCELLED".equals(lifecycle)) reasons.add("CANCELLED");
        if ("POSTPONED".equals(lifecycle)) reasons.add("POSTPONED");
        if ("UNKNOWN".equals(lifecycle)) warnings.add("LIFECYCLE_UNKNOWN");

        String type = location.path("location_type").asText("UNKNOWN");
        if ("PHYSICAL".equals(type)) physical(location, reasons, warnings);
        else if (!"ONLINE".equals(type)) reasons.add("UNKNOWN_LOCATION_TYPE");

        Instant cutoff = null;
        Instant start = null;
        if (slot == null || slot.isNull()) reasons.add("MISSING_SCHEDULE");
        else {
            if (slot.path("retired").asBoolean()) reasons.add("OCCURRENCE_REMOVED");
            for (JsonNode other : input.path("slots")) {
                if (other.path("retired").asBoolean() || other.path("id").equals(slot.path("id"))) continue;
                boolean sameInstant = slot.hasNonNull("starts_at") && slot.path("starts_at").equals(other.path("starts_at"));
                boolean sameLocal = slot.hasNonNull("local_start_date")
                        && slot.path("local_start_date").equals(other.path("local_start_date"))
                        && slot.path("local_start_time").equals(other.path("local_start_time"))
                        && slot.path("timezone").equals(other.path("timezone"));
                if (sameInstant || sameLocal) reasons.add("AMBIGUOUS_OCCURRENCE");
            }
            if (!"KNOWN".equals(slot.path("start_date_status").asText())) reasons.add("START_DATE_UNKNOWN");
            if (!"KNOWN".equals(slot.path("end_date_status").asText())
                    && (slot.hasNonNull("ends_at") || slot.hasNonNull("local_end_date"))) reasons.add("CONFLICTING_END_DATE");
            ZoneId zone = zone(slot);
            start = instant(slot, "starts_at");
            Instant end = instant(slot, "ends_at");
            LocalDate date = date(slot, "local_start_date");
            LocalDate endDate = date(slot, "local_end_date");
            LocalTime time = time(slot, "local_start_time");
            LocalTime endTime = time(slot, "local_end_time");
            if (date == null && start != null && zone != null) date = start.atZone(zone).toLocalDate();
            if (date == null) reasons.add("MISSING_START_DATE");
            if (zone == null) reasons.add("MISSING_TIMEZONE");
            String timeStatus = slot.path("start_time_status").asText("UNKNOWN");
            if ("KNOWN".equals(timeStatus) && start == null) reasons.add("UNRESOLVED_START_TIME");
            if (!"KNOWN".equals(timeStatus)) {
                reasons.add("START_TIME_UNKNOWN");
                if (start != null || time != null) reasons.add("CONFLICTING_START_TIME");
            }
            if (!"KNOWN".equals(slot.path("end_time_status").asText()) && (end != null || endTime != null)) {
                reasons.add("CONFLICTING_END_TIME");
            }
            if ((end != null && start != null && end.isBefore(start))
                    || (endDate != null && date != null && endDate.isBefore(date))
                    || (endDate != null && endDate.equals(date) && time != null && endTime != null && endTime.isBefore(time))) {
                reasons.add("END_BEFORE_START");
            }
            checkPoint(slot, "start", start, zone, reasons);
            checkPoint(slot, "end", end, zone, reasons);
            if (end != null && "KNOWN".equals(slot.path("end_date_status").asText())
                    && "KNOWN".equals(slot.path("end_time_status").asText())
                    && !slot.path("end_approximate").asBoolean()) cutoff = end;
            else if (date != null && zone != null) {
                LocalDate last = "KNOWN".equals(slot.path("end_date_status").asText()) && endDate != null ? endDate : date;
                cutoff = last.plusDays(1).atStartOfDay(zone).toInstant();
                warnings.add("DISCOVERY_CUTOFF_INFERRED");
            }
            if (cutoff != null) {
                if (!cutoff.isAfter(now)) reasons.add("EXPIRED");
                else next = earlier(next, cutoff);
            }
            if (start != null && start.isAfter(now)) next = earlier(next, start);
        }
        String state = reasons.contains("CANCELLED") ? "CANCELLED"
                : reasons.contains("POSTPONED") ? "POSTPONED"
                : reasons.contains("EXPIRED") ? "EXPIRED"
                : !reasons.isEmpty() ? "UNAVAILABLE"
                : start == null ? "DATE_CONFIRMED"
                : start.isAfter(now) ? "UPCOMING"
                : slot != null && instant(slot, "ends_at") != null && !slot.path("end_approximate").asBoolean()
                    ? "ONGOING" : "STARTED_END_UNKNOWN";
        return new Decision(List.copyOf(reasons), List.copyOf(warnings), next, cutoff, state);
    }

    private void physical(JsonNode location, Set<String> reasons, Set<String> warnings) {
        String country = text(location, "country_code");
        if (country == null) reasons.add("MISSING_COUNTRY");
        else if (!countries.contains(country.toUpperCase(Locale.ROOT))) reasons.add("OUT_OF_SCOPE_COUNTRY");
        if (text(location, "city") == null) reasons.add("MISSING_CITY");
        if (text(location, "venue_name") == null && text(location, "address") == null) reasons.add("MISSING_DESTINATION");
        JsonNode lat = location.path("latitude"), lon = location.path("longitude");
        if (!lat.isNumber() || !lon.isNumber()) reasons.add("MISSING_COORDINATES");
        else if (!Double.isFinite(lat.asDouble()) || !Double.isFinite(lon.asDouble())
                || Math.abs(lat.asDouble()) > 90 || Math.abs(lon.asDouble()) > 180
                || (lat.asDouble() == 0 && lon.asDouble() == 0)) reasons.add("INVALID_COORDINATES");
        else if ("NL".equalsIgnoreCase(country)
                && (lat.asDouble() < 50.7 || lat.asDouble() > 53.7 || lon.asDouble() < 3.0 || lon.asDouble() > 7.3)) {
            reasons.add("COUNTRY_COORDINATE_CONFLICT");
        }
        if (!Set.of("SOURCE_VENUE", "SOURCE_EVENT", "VERIFIED_VENUE", "VERIFIED_ADDRESS")
                .contains(location.path("coordinate_evidence").asText())) reasons.add("UNVERIFIED_COORDINATE_ORIGIN");
        else if (location.path("coordinate_evidence").asText().startsWith("SOURCE_")) {
            warnings.add("SOURCE_LOCATION_NOT_INDEPENDENTLY_VERIFIED");
        }
    }

    private static void checkPoint(JsonNode slot, String prefix, Instant instant, ZoneId zone, Set<String> reasons) {
        if (instant == null || zone == null) return;
        LocalDate date = date(slot, "local_" + prefix + "_date");
        LocalTime time = time(slot, "local_" + prefix + "_time");
        ZonedDateTime local = instant.atZone(zone);
        if ((date != null && !date.equals(local.toLocalDate())) || (time != null && !time.equals(local.toLocalTime()))) {
            reasons.add("CONFLICTING_" + prefix.toUpperCase(Locale.ROOT) + "_TIME");
        }
    }

    public static boolean publicUrl(String value) {
        if (value == null) return false;
        try {
            URI uri = URI.create(value);
            String host = uri.getHost();
            if (host == null || uri.getUserInfo() != null
                    || !("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))) return false;
            host = host.toLowerCase(Locale.ROOT);
            return host.contains(".") && !host.endsWith(".") && !host.matches("[0-9.]+")
                    && !host.contains(":") && !host.endsWith(".localhost") && !host.endsWith(".local")
                    && !host.endsWith(".internal") && !host.endsWith(".test") && !host.endsWith(".invalid");
        } catch (IllegalArgumentException exception) { return false; }
    }

    static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() && !value.asText().isBlank() ? value.asText().strip() : null;
    }
    static Instant instant(JsonNode node, String field) {
        String value = text(node, field);
        try { return value == null ? null : OffsetDateTime.parse(value).toInstant(); }
        catch (DateTimeException exception) { return null; }
    }
    private static LocalDate date(JsonNode node, String field) {
        try { String value = text(node, field); return value == null ? null : LocalDate.parse(value); }
        catch (DateTimeException exception) { return null; }
    }
    private static LocalTime time(JsonNode node, String field) {
        try { String value = text(node, field); return value == null ? null : LocalTime.parse(value); }
        catch (DateTimeException exception) { return null; }
    }
    private static ZoneId zone(JsonNode slot) {
        try { String value = text(slot, "timezone"); return value == null ? null : ZoneId.of(value); }
        catch (DateTimeException exception) { return null; }
    }
    private static Instant earlier(Instant left, Instant right) { return left.isBefore(right) ? left : right; }
}
