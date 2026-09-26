package nl.loc.data.source.ticketmaster;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import nl.loc.data.event.Category;
import nl.loc.data.event.CategoryAssignment;

/** Maps Ticketmaster segment names. Placeholder genre names are not categories. */
final class TicketmasterCategoryMapper {
    private static final Set<String> PLACEHOLDERS = Set.of(
            "ongedefinieerd",
            "anders",
            "diverse",
            "undefined",
            "miscellaneous",
            "other"
    );

    private TicketmasterCategoryMapper() {
    }

    static List<Category> map(JsonNode root) {
        List<Category> matched = new ArrayList<>();
        JsonNode classifications = root.path("classifications");
        if (classifications.isArray()) {
            for (JsonNode classification : classifications) {
                Category category = fromSegment(classificationName(classification, "segment"));
                if (category != null) {
                    matched.add(category);
                }
            }
        }
        return CategoryAssignment.resolve(matched);
    }

    private static Category fromSegment(String segment) {
        if (segment == null || PLACEHOLDERS.contains(segment)) {
            return null;
        }
        return switch (segment) {
            case "muziek", "music" -> Category.MUSIC_AND_NIGHTLIFE;
            case "sport", "sports" -> Category.SPORTS;
            case "cultuur", "arts & theatre", "arts and theatre" -> Category.ARTS_AND_CULTURE;
            default -> null;
        };
    }

    private static String classificationName(JsonNode classification, String field) {
        JsonNode name = classification.path(field).path("name");
        if (!name.isTextual() || name.asText().isBlank()) {
            return null;
        }
        return name.asText().strip().toLowerCase(Locale.ROOT);
    }
}
