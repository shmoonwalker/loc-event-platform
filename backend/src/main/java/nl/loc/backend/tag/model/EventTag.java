package nl.loc.backend.tag.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Discovery tags published on an event. Slugs match the data application's tag list. */
@Schema(
        description = "Discovery tag. The API value is the slug, not the enum name.",
        allowableValues = {
                "concert", "festival", "club-night", "dj-set", "live-music",
                "theatre", "comedy", "exhibition", "screening", "workshop",
                "conference", "meetup", "networking", "fair", "tour",
                "family-friendly", "kids", "students", "professionals", "beginners",
                "18-plus", "indoor", "outdoor", "free", "ticketed",
                "evening", "daytime", "techno", "house", "jazz",
                "classical", "pop", "rock", "hip-hop", "football",
                "running", "cycling", "fitness", "food-and-drink", "wellness",
                "startups", "climate", "coding", "arts", "sports",
                "business", "technology", "learning", "nature", "other"
        })
public enum EventTag {
    CONCERT("concert"),
    FESTIVAL("festival"),
    CLUB_NIGHT("club-night"),
    DJ_SET("dj-set"),
    LIVE_MUSIC("live-music"),
    THEATRE("theatre"),
    COMEDY("comedy"),
    EXHIBITION("exhibition"),
    SCREENING("screening"),
    WORKSHOP("workshop"),
    CONFERENCE("conference"),
    MEETUP("meetup"),
    NETWORKING("networking"),
    FAIR("fair"),
    TOUR("tour"),
    FAMILY_FRIENDLY("family-friendly"),
    KIDS("kids"),
    STUDENTS("students"),
    PROFESSIONALS("professionals"),
    BEGINNERS("beginners"),
    EIGHTEEN_PLUS("18-plus"),
    INDOOR("indoor"),
    OUTDOOR("outdoor"),
    FREE("free"),
    TICKETED("ticketed"),
    EVENING("evening"),
    DAYTIME("daytime"),
    TECHNO("techno"),
    HOUSE("house"),
    JAZZ("jazz"),
    CLASSICAL("classical"),
    POP("pop"),
    ROCK("rock"),
    HIP_HOP("hip-hop"),
    FOOTBALL("football"),
    RUNNING("running"),
    CYCLING("cycling"),
    FITNESS("fitness"),
    FOOD_AND_DRINK("food-and-drink"),
    WELLNESS("wellness"),
    STARTUPS("startups"),
    CLIMATE("climate"),
    CODING("coding"),
    ARTS("arts"),
    SPORTS("sports"),
    BUSINESS("business"),
    TECHNOLOGY("technology"),
    LEARNING("learning"),
    NATURE("nature"),
    OTHER("other");

    private static final Map<String, EventTag> BY_SLUG = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(EventTag::slug, Function.identity()));

    private final String slug;

    EventTag(String slug) {
        this.slug = slug;
    }

    public String slug() {
        return slug;
    }

    public String label() {
        return switch (this) {
            case DJ_SET -> "DJ set";
            case EIGHTEEN_PLUS -> "18 plus";
            case HIP_HOP -> "Hip hop";
            default -> {
                String words = slug.replace('-', ' ');
                yield Character.toUpperCase(words.charAt(0)) + words.substring(1);
            }
        };
    }

    public static Optional<EventTag> fromSlug(String slug) {
        if (slug == null || slug.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_SLUG.get(slug.strip().toLowerCase(Locale.ROOT)));
    }
}
