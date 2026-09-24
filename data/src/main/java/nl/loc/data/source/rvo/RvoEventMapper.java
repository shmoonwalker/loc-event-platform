package nl.loc.data.source.rvo;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import nl.loc.data.event.EventLocation;
import nl.loc.data.event.EventTimeSlot;
import nl.loc.data.event.LocationType;
import nl.loc.data.event.NormalizedEvent;

@Component
public class RvoEventMapper {

    private static final String SOURCE = "rvo";
    private static final String SITE_ORIGIN = "https://www.rvo.nl";

    private final ObjectMapper objectMapper = new ObjectMapper();

    public NormalizedEvent map(String detailJson, String rawObjectKey) {
        if (detailJson == null || detailJson.isBlank()) {
            throw new IllegalArgumentException("RVO detail JSON is missing");
        }

        JsonNode root = readObject(detailJson);

        String externalId = text(root, "id");
        if (externalId == null) {
            throw new IllegalArgumentException("RVO detail JSON is missing id");
        }

        return new NormalizedEvent(
                SOURCE,
                externalId,
                blankToNull(rawObjectKey),
                text(root, "title"),
                text(root, "intro"),
                absoluteUrl(text(root, "url")),
                text(root, "link"),
                organizerNames(root),
                parseInstant(text(root, "created")),
                parseInstant(text(root, "changed")),
                mapLocation(root),
                mapTimeSlots(root)
        );
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

    private static EventLocation mapLocation(JsonNode root) {
        LocationType type = locationType(root.get("isOnline"));
        boolean online = type == LocationType.ONLINE;
        return new EventLocation(
                type,
                text(root, "locationName"),
                joinAddresses(root.get("addresses")),
                text(root, "locality"),
                text(root, "postalCode"),
                text(root, "country"),
                online ? null : number(root, "latitude"),
                online ? null : number(root, "longitude")
        );
    }

    private static LocationType locationType(JsonNode isOnline) {
        if (isOnline == null || isOnline.isNull() || !isOnline.isBoolean()) {
            return LocationType.UNKNOWN;
        }
        return isOnline.booleanValue() ? LocationType.ONLINE : LocationType.PHYSICAL;
    }

    private static List<EventTimeSlot> mapTimeSlots(JsonNode root) {
        JsonNode dates = root.get("dates");
        if (dates == null || !dates.isArray() || dates.isEmpty()) {
            return List.of();
        }

        List<EventTimeSlot> slots = new ArrayList<>();
        for (JsonNode date : dates) {
            if (date == null || !date.isObject()) {
                continue;
            }
            Instant startsAt = parseInstant(text(date, "value"));
            Instant endsAt = parseInstant(text(date, "end_value"));
            if (startsAt == null && endsAt == null) {
                continue;
            }
            slots.add(new EventTimeSlot(startsAt, endsAt));
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

    private static Instant parseInstant(String value) {
        if (value == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeParseException exception) {
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