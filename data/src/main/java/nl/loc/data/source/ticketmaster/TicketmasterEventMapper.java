package nl.loc.data.source.ticketmaster;

import java.math.BigDecimal;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import lombok.extern.slf4j.Slf4j;
import nl.loc.data.event.EventImage;
import nl.loc.data.event.EventLifecycle;
import nl.loc.data.event.EventLocation;
import nl.loc.data.event.LocationType;
import nl.loc.data.event.NormalizedEvent;
import nl.loc.data.processing.SourceEventMapper;
import nl.loc.data.text.HtmlPlainText;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.Assert;

@Slf4j
@Component
public class TicketmasterEventMapper implements SourceEventMapper {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${loc.publication.online-venue-ids:}")
    private String onlineVenueIds = "";

    @Override
    public String source() {
        return "ticketmaster";
    }

    @Override
    public List<NormalizedEvent> map(String rawJson, String rawObjectKey) {
        Assert.hasText(rawJson, "Ticketmaster event JSON is required");
        Assert.hasText(rawObjectKey, "rawObjectKey is required");
        JsonNode root = readObject(rawJson);
        String id = text(root, "id");
        Assert.hasText(id, "Ticketmaster event JSON is missing id");
        List<String> issues = new ArrayList<>();
        JsonNode venue = selectVenue(root, issues);
        if (root.path("test").asBoolean(false)) issues.add("TEST_EVENT");
        for (JsonNode classification : root.path("classifications")) {
            for (String field : List.of("type", "subType")) {
                String kind = text(classification.path(field), "name");
                if (kind != null && Set.of("parking", "merchandise", "ticket upgrade", "upgrade").contains(kind.toLowerCase(Locale.ROOT))) {
                    issues.add("OUT_OF_SCOPE_PRODUCT");
                }
                if (kind != null && Set.of("season ticket", "season tickets", "season pass").contains(kind.toLowerCase(Locale.ROOT))) issues.add("SEASON_PASS");
            }
        }
        String sourceUrl = httpUrl(text(root, "url"));
        return List.of(new NormalizedEvent(
                source(), id, rawObjectKey, text(root, "name"), mapDescription(root),
                sourceUrl, promoterNames(root),
                // Discovery does not provide a documented event modification timestamp.
                null, null, mapLocation(venue), TicketmasterTimeMapper.map(root, venue),
                mapLifecycle(root), mapImages(root),
                TicketmasterCategoryMapper.map(root), issues.stream().distinct().toList(),
                TicketmasterCategoryMapper.tags(root)));
    }

    private JsonNode readObject(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root == null || !root.isObject()) {
                throw new IllegalArgumentException("Ticketmaster event JSON must be an object");
            }
            return root;
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Could not parse Ticketmaster event JSON", exception);
        }
    }

    private static String mapDescription(JsonNode root) {
        Set<String> sections = new LinkedHashSet<>();
        for (String field : List.of("description", "info", "additionalInfo", "pleaseNote")) {
            String content = HtmlPlainText.convert(text(root, field));
            if (content != null) {
                sections.add(content);
            }
        }
        return sections.isEmpty() ? null : String.join("\n\n", sections);
    }

    private static JsonNode selectVenue(JsonNode root, List<String> issues) {
        JsonNode venues = root.path("_embedded").path("venues");
        java.util.Map<String, JsonNode> distinct = new java.util.LinkedHashMap<>();
        if (venues.isArray()) {
            for (JsonNode venue : venues) {
                if (venue.isObject()) {
                    String id = text(venue, "id");
                    String key = id == null ? venue.toString() : id;
                    JsonNode previous = distinct.get(key);
                    if (previous == null || venueScore(venue) > venueScore(previous)) distinct.put(key, venue);
                }
            }
        }
        if (distinct.size() > 1) issues.add("AMBIGUOUS_VENUE");
        return distinct.values().stream().max(java.util.Comparator.comparingInt(TicketmasterEventMapper::venueScore))
                .orElse(MissingNode.getInstance());
    }

    private static int venueScore(JsonNode venue) {
        return (coordinate(venue.path("location"), "latitude", 90) != null
                && coordinate(venue.path("location"), "longitude", 180) != null ? 4 : 0)
                + (text(venue.path("city"), "name") != null ? 2 : 0)
                + (text(venue.path("address"), "line1") != null ? 1 : 0);
    }

    private EventLocation mapLocation(JsonNode venue) {
        if (!venue.isObject()) {
            return null;
        }
        String venueId = text(venue, "id");
        if (venueId != null && java.util.Arrays.stream(onlineVenueIds.split(","))
                .map(String::strip).anyMatch(venueId::equals)) {
            return new EventLocation(LocationType.ONLINE, text(venue, "name"), null, null,
                    null, null, null, null, venueId, null, "NOT_APPLICABLE");
        }
        List<String> lines = new ArrayList<>();
        for (String field : List.of("line1", "line2", "line3")) {
            String value = text(venue.path("address"), field);
            if (value != null) {
                lines.add(value);
            }
        }
        Double latitude = coordinate(venue.path("location"), "latitude", 90);
        Double longitude = coordinate(venue.path("location"), "longitude", 180);
        LocationType type = !lines.isEmpty() || (latitude != null && longitude != null)
                ? LocationType.PHYSICAL : LocationType.UNKNOWN;
        String countryCode = text(venue.path("country"), "countryCode");
        return new EventLocation(type, text(venue, "name"),
                lines.isEmpty() ? null : String.join(", ", lines),
                text(venue.path("city"), "name"), text(venue, "postalCode"),
                text(venue.path("country"), "name"), latitude, longitude, text(venue, "id"),
                countryCode == null ? null : countryCode.toUpperCase(Locale.ROOT),
                latitude != null && longitude != null ? "SOURCE_VENUE" : "UNKNOWN");
    }

    private static List<String> promoterNames(JsonNode root) {
        Set<String> names = new LinkedHashSet<>();
        JsonNode promoters = root.path("promoters");
        if (promoters.isArray()) {
            for (JsonNode promoter : promoters) {
                String name = text(promoter, "name");
                if (name != null) {
                    names.add(name);
                }
            }
        }
        String name = text(root.path("promoter"), "name");
        if (name != null) {
            names.add(name);
        }
        return List.copyOf(names);
    }

    private static EventLifecycle mapLifecycle(JsonNode root) {
        String code = text(root.path("dates").path("status"), "code");
        if (code == null) {
            return EventLifecycle.UNKNOWN;
        }
        return switch (code.toLowerCase(Locale.ROOT)) {
            case "canceled", "cancelled" -> EventLifecycle.CANCELLED;
            case "postponed" -> EventLifecycle.POSTPONED;
            case "rescheduled" -> EventLifecycle.RESCHEDULED;
            case "onsale", "offsale" -> EventLifecycle.SCHEDULED;
            default -> EventLifecycle.UNKNOWN;
        };
    }

    private static List<EventImage> mapImages(JsonNode root) {
        JsonNode images = root.path("images");
        List<EventImage> result = new ArrayList<>();
        Set<String> seenUrls = new LinkedHashSet<>();
        if (images.isArray()) {
            for (JsonNode image : images) {
                String url = httpUrl(text(image, "url"));
                if (url == null || !seenUrls.add(url)) {
                    continue;
                }
                Boolean fallback = image.path("fallback").isBoolean() ? image.get("fallback").booleanValue() : null;
                result.add(new EventImage(url, positiveInteger(image, "width"), positiveInteger(image, "height"),
                        text(image, "ratio"), text(image, "attribution"), fallback));
            }
        }
        return List.copyOf(result);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if ((!value.isTextual() && !value.isNumber()) || value.asText().isBlank()) {
            return null;
        }
        return value.asText().strip();
    }

    private static BigDecimal decimal(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static Double coordinate(JsonNode node, String field, double limit) {
        BigDecimal value = decimal(node, field);
        if (value == null) {
            return null;
        }
        double coordinate = value.doubleValue();
        return Double.isFinite(coordinate) && Math.abs(coordinate) <= limit ? coordinate : null;
    }

    private static Integer positiveInteger(JsonNode node, String field) {
        BigDecimal value = decimal(node, field);
        if (value == null) {
            return null;
        }
        try {
            int integer = value.intValueExact();
            return integer > 0 ? integer : null;
        } catch (ArithmeticException exception) {
            return null;
        }
    }

    private static String httpUrl(String value) {
        if (value == null) {
            return null;
        }
        try {
            URI uri = URI.create(value);
            return ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null ? value : null;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
