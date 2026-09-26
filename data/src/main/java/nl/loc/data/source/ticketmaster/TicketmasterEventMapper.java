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
import nl.loc.data.event.EventPriceRange;
import nl.loc.data.event.LocationType;
import nl.loc.data.event.NormalizedEvent;
import nl.loc.data.processing.SourceEventMapper;
import nl.loc.data.text.HtmlPlainText;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

@Slf4j
@Component
public class TicketmasterEventMapper implements SourceEventMapper {
    private final ObjectMapper objectMapper = new ObjectMapper();

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
        JsonNode venue = firstVenue(root);
        String sourceUrl = httpUrl(text(root, "url"));
        return List.of(new NormalizedEvent(
                source(), id, rawObjectKey, text(root, "name"), mapDescription(root),
                sourceUrl, null, promoterNames(root),
                // Discovery does not provide a documented event modification timestamp.
                null, null, mapLocation(venue), TicketmasterTimeMapper.map(root, venue),
                mapLifecycle(root), mapPriceRanges(root), mapImages(root)));
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
        for (String field : List.of("description", "info", "additionalInfo")) {
            String content = HtmlPlainText.convert(text(root, field));
            if (content != null) {
                sections.add(content);
            }
        }
        return sections.isEmpty() ? null : String.join("\n\n", sections);
    }

    private static JsonNode firstVenue(JsonNode root) {
        JsonNode venues = root.path("_embedded").path("venues");
        if (venues.isArray()) {
            for (JsonNode venue : venues) {
                if (venue.isObject()) {
                    // The catalog currently supports one location; all venues remain in raw storage.
                    return venue;
                }
            }
        }
        return MissingNode.getInstance();
    }

    private static EventLocation mapLocation(JsonNode venue) {
        if (!venue.isObject()) {
            return null;
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
                countryCode == null ? null : countryCode.toUpperCase(Locale.ROOT));
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

    private static List<EventPriceRange> mapPriceRanges(JsonNode root) {
        JsonNode ranges = root.path("priceRanges");
        Set<EventPriceRange> result = new LinkedHashSet<>();
        if (ranges.isArray()) {
            for (JsonNode range : ranges) {
                BigDecimal min = decimal(range, "min");
                BigDecimal max = decimal(range, "max");
                if ((min == null && max == null)
                        || (min != null && min.signum() < 0) || (max != null && max.signum() < 0)
                        || (min != null && max != null && min.compareTo(max) > 0)) {
                    log.warn("Ignoring invalid Ticketmaster price range eventId={}", text(root, "id"));
                    continue;
                }
                String currency = text(range, "currency");
                result.add(new EventPriceRange(min, max,
                        currency == null ? null : currency.toUpperCase(Locale.ROOT), text(range, "type")));
            }
        }
        return List.copyOf(result);
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
