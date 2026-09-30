package nl.loc.data.source.ticketmaster;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import nl.loc.data.event.Category;
import nl.loc.data.event.CategoryAssignment;
import nl.loc.data.tagging.ContentTag;

/** Prefers segment IDs, with explicit name and genre fallbacks for incomplete classifications. */
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
                Category category = fromSegmentId(classification.path("segment").path("id").asText(""));
                if (category == null) {
                    category = fromSegment(classificationName(classification, "segment"));
                }
                if (category == null) {
                    category = fromGenre(classificationName(classification, "subGenre"));
                }
                if (category == null) {
                    category = fromGenre(classificationName(classification, "genre"));
                }
                if (category != null) {
                    matched.add(category);
                }
            }
        }
        return CategoryAssignment.resolve(matched);
    }

    private static Category fromSegmentId(String id) {
        return switch (id) {
            case "KZFzniwnSyZfZ7v7nJ" -> Category.MUSIC_AND_NIGHTLIFE;
            case "KZFzniwnSyZfZ7v7nE" -> Category.SPORTS;
            case "KZFzniwnSyZfZ7v7na", "KZFzniwnSyZfZ7v7nn" -> Category.ARTS_AND_CULTURE;
            default -> null;
        };
    }

    private static Category fromSegment(String segment) {
        if (segment == null || PLACEHOLDERS.contains(segment)) {
            return null;
        }
        return switch (segment) {
            case "muziek", "music" -> Category.MUSIC_AND_NIGHTLIFE;
            case "sport", "sports" -> Category.SPORTS;
            case "cultuur", "arts & theatre", "arts and theatre", "film", "films" -> Category.ARTS_AND_CULTURE;
            default -> null;
        };
    }

    private static Category fromGenre(String genre) {
        if (genre == null || PLACEHOLDERS.contains(genre)) {
            return null;
        }
        // Exact, unambiguous labels only: broad labels such as Family or Festival
        // do not establish a Loc category. A recognized segment always wins.
        return switch (genre) {
            case "rock", "pop", "jazz", "classical", "klassiek", "hip-hop/rap",
                    "r&b", "blues", "country", "metal", "reggae", "folk", "dance/electronic"
                    -> Category.MUSIC_AND_NIGHTLIFE;
            case "theatre", "theater", "comedy", "komedie", "opera", "ballet",
                    "dance", "dans", "fine art", "fine arts", "classical/vocal",
                    "magic & illusion", "magic", "film", "films", "animation", "animatie"
                    -> Category.ARTS_AND_CULTURE;
            case "football", "soccer", "voetbal", "basketball", "basketbal",
                    "baseball", "honkbal", "hockey", "ice hockey", "ijshockey",
                    "tennis", "rugby", "volleyball", "volleybal", "boxing", "boksen",
                    "wrestling", "mixed martial arts", "netball"
                    -> Category.SPORTS;
            default -> null;
        };
    }

    /** Specific tags from Ticketmaster's own genre labels, so events without a description are still findable. */
    static List<ContentTag> tags(JsonNode root) {
        Set<ContentTag> tags = new LinkedHashSet<>();
        for (JsonNode classification : root.path("classifications")) {
            for (String field : List.of("genre", "subGenre", "subType")) {
                ContentTag tag = fromLabel(classificationName(classification, field));
                if (tag != null) tags.add(tag);
            }
            if (classification.path("family").asBoolean(false)) tags.add(ContentTag.FAMILY_FRIENDLY);
        }
        return List.copyOf(tags);
    }

    private static ContentTag fromLabel(String label) {
        if (label == null || PLACEHOLDERS.contains(label)) {
            return null;
        }
        return switch (label) {
            case "rock", "hard rock", "alternative rock", "punk", "metal" -> ContentTag.ROCK;
            case "pop", "dutch pop", "nederpop" -> ContentTag.POP;
            case "jazz", "blues", "jazz & blues" -> ContentTag.JAZZ;
            case "classical", "klassiek", "classical/vocal", "opera" -> ContentTag.CLASSICAL;
            case "hip-hop/rap", "hip-hop", "rap" -> ContentTag.HIP_HOP;
            case "house" -> ContentTag.HOUSE;
            case "techno" -> ContentTag.TECHNO;
            case "comedy", "komedie", "cabaret", "stand-up" -> ContentTag.COMEDY;
            case "theatre", "theater", "musical", "musicals" -> ContentTag.THEATRE;
            case "film", "films", "animation", "animatie" -> ContentTag.SCREENING;
            case "football", "soccer", "voetbal" -> ContentTag.FOOTBALL;
            case "cycling", "wielrennen" -> ContentTag.CYCLING;
            case "running", "hardlopen", "marathon" -> ContentTag.RUNNING;
            case "festival", "festivals" -> ContentTag.FESTIVAL;
            case "family", "familie", "children's theatre", "kindervoorstelling" -> ContentTag.FAMILY_FRIENDLY;
            case "fine art", "fine arts" -> ContentTag.EXHIBITION;
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
