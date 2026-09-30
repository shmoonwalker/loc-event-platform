package nl.loc.data.source.rvo;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import nl.loc.data.event.EventLocation;
import nl.loc.data.event.EventLifecycle;
import nl.loc.data.event.EventTimeSlot;
import nl.loc.data.event.LocationType;
import nl.loc.data.event.NormalizedEvent;
import nl.loc.data.processing.SourceEventMapper;
import nl.loc.data.text.HtmlPlainText;

@Slf4j
@Component
public class RvoEventMapper implements SourceEventMapper {

    private static final String SOURCE = "rvo";
    private static final String SITE_ORIGIN = "https://www.rvo.nl";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String source() {
        return SOURCE;
    }

    @Override
    public List<NormalizedEvent> map(String detailJson, String rawObjectKey) {
        if (detailJson == null || detailJson.isBlank()) {
            throw new IllegalArgumentException("RVO detail JSON is missing");
        }

        JsonNode root = readObject(detailJson);

        String externalId = text(root, "id");
        if (externalId == null) {
            throw new IllegalArgumentException("RVO detail JSON is missing id");
        }

        return List.of(new NormalizedEvent(
                SOURCE,
                externalId,
                blankToNull(rawObjectKey),
                text(root, "title"),
                mapDescription(root),
                absoluteUrl(text(root, "url")),
                organizerNames(root),
                parseInstant(text(root, "created"), "created", rawObjectKey),
                parseInstant(text(root, "changed"), "changed", rawObjectKey),
                mapLocation(root),
                mapTimeSlots(root, rawObjectKey),
                EventLifecycle.UNKNOWN,
                List.of(),
                RvoCategoryMapper.map(root),
                List.of(),
                RvoCategoryMapper.tags(root)
        ));
    }

    private JsonNode readObject(String detailJson) {
        try {
            JsonNode root = objectMapper.readTree(detailJson);
            if (root == null || !root.isObject()) {
                throw new IllegalArgumentException(
                        "RVO detail JSON must be an object, not a list-page array");
            }
            return root;
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Could not parse RVO detail JSON", exception);
        }
    }

    private static String mapDescription(JsonNode root) {
        List<String> sections = new ArrayList<>();
        String intro = HtmlPlainText.convert(text(root, "intro"));
        if (intro != null) {
            sections.add(intro);
        }

        JsonNode body = root.get("body");
        if (body != null && body.isArray()) {
            for (JsonNode section : body) {
                if (section.isTextual()) {
                    String content = HtmlPlainText.convert(section.asText());
                    if (content != null) {
                        sections.add(content);
                    }
                }
            }
        }
        return sections.isEmpty() ? null : String.join("\n\n", sections);
    }

    private static EventLocation mapLocation(JsonNode root) {
        LocationType type = locationType(root.get("isOnline"));
        boolean online = type == LocationType.ONLINE;
        Double latitude = online ? null : number(root, "latitude");
        Double longitude = online ? null : number(root, "longitude");
        String evidence = online ? "NOT_APPLICABLE"
                : latitude != null && longitude != null ? "SOURCE_EVENT" : "UNKNOWN";
        return new EventLocation(
                type,
                text(root, "locationName"),
                joinAddresses(root.get("addresses")),
                text(root, "locality"),
                text(root, "postalCode"),
                text(root, "country"),
                latitude,
                longitude,
                null, type == LocationType.PHYSICAL ? countryCode(text(root, "country")) : null, evidence
        );
    }

    /**
     * RVO sends official Dutch names such as "Bondsrepubliek Duitsland" and omits the country for
     * events in the Netherlands. The longest Dutch short name contained in the value wins.
     */
    private static String countryCode(String country) {
        if (country == null) return "NL";
        String value = country.strip().toLowerCase(DUTCH);
        if (value.length() == 2) return value.toUpperCase(java.util.Locale.ROOT);
        if (value.contains("nederland")) return "NL";
        for (var alias : COUNTRY_ALIASES.entrySet()) {
            if (value.contains(alias.getKey())) return alias.getValue();
        }
        String match = null;
        int matchLength = 0;
        for (String code : java.util.Locale.getISOCountries()) {
            String name = java.util.Locale.of("", code).getDisplayCountry(DUTCH).toLowerCase(DUTCH);
            if (name.length() > matchLength && value.contains(name)) {
                match = code;
                matchLength = name.length();
            }
        }
        return match;
    }

    private static final java.util.Locale DUTCH = java.util.Locale.of("nl");

    /** Official names that use an adjective or a spelling the JDK's Dutch country names do not. */
    private static final java.util.Map<String, String> COUNTRY_ALIASES = java.util.Map.of(
            "portugese", "PT", "argentijnse", "AR", "franse", "FR", "tsjechische", "CZ",
            "italiaanse", "IT", "mexicaanse", "MX", "saudi-arabië", "SA", "republiek korea", "KR");

    private static LocationType locationType(JsonNode isOnline) {
        if (isOnline == null || isOnline.isNull() || !isOnline.isBoolean()) {
            return LocationType.UNKNOWN;
        }
        return isOnline.booleanValue() ? LocationType.ONLINE : LocationType.PHYSICAL;
    }

    private static List<EventTimeSlot> mapTimeSlots(JsonNode root, String rawObjectKey) {
        JsonNode dates = root.get("dates");
        if (dates == null || !dates.isArray() || dates.isEmpty()) {
            return List.of();
        }

        List<EventTimeSlot> slots = new ArrayList<>();
        for (int index = 0; index < dates.size(); index++) {
            JsonNode date = dates.get(index);
            if (date == null || !date.isObject()) {
                continue;
            }
            OffsetDateTime startsAt = parseOffsetDateTime(text(date, "value"), "dates[" + index + "].value", rawObjectKey);
            OffsetDateTime endsAt = parseOffsetDateTime(text(date, "end_value"), "dates[" + index + "].end_value", rawObjectKey);
            if (startsAt == null && endsAt == null) {
                continue;
            }
            slots.add(EventTimeSlot.fromOffsetDateTimes(startsAt, endsAt));
        }
        return List.copyOf(slots);
    }

    private static List<String> organizerNames(JsonNode root) {
        JsonNode organisers = root.get("organisers");
        if (organisers == null || !organisers.isArray() || organisers.isEmpty()) {
            return List.of();
        }

        List<String> names = new ArrayList<>();
        for (JsonNode organiser : organisers) {
            if (organiser == null || !organiser.isTextual()) {
                continue;
            }
            String name = blankToNull(organiser.asText());
            if (name != null) {
                names.add(name);
            }
        }
        return List.copyOf(names);
    }

    private static String absoluteUrl(String url) {
        if (url == null) {
            return null;
        }
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url;
        }
        if (url.startsWith("/")) {
            return SITE_ORIGIN + url;
        }
        return SITE_ORIGIN + "/" + url;
    }

    private static Instant parseInstant(String value, String field, String rawObjectKey) {
        OffsetDateTime parsed = parseOffsetDateTime(value, field, rawObjectKey);
        return parsed == null ? null : parsed.toInstant();
    }

    private static OffsetDateTime parseOffsetDateTime(String value, String field, String rawObjectKey) {
        if (value == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value);
        } catch (DateTimeParseException exception) {
            log.warn("Ignoring invalid RVO timestamp rawObjectKey={} field={}", rawObjectKey, field);
            return null;
        }
    }

    private static String joinAddresses(JsonNode addresses) {
        if (addresses == null || !addresses.isArray()) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        for (JsonNode address : addresses) {
            if (address != null && address.isTextual()) {
                String part = blankToNull(address.asText());
                if (part != null) {
                    parts.add(part);
                }
            }
        }
        return parts.isEmpty() ? null : String.join(", ", parts);
    }

    private static Double number(JsonNode parent, String field) {
        JsonNode node = parent.get(field);
        if (node == null || node.isNull() || !node.isNumber()) {
            return null;
        }
        return node.doubleValue();
    }

    private static String text(JsonNode parent, String field) {
        JsonNode node = parent.get(field);
        if (node == null || node.isNull() || !node.isValueNode() || node.isBoolean()) {
            return null;
        }
        return blankToNull(node.asText());
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }
}
