package nl.loc.data.tagging;

import java.util.Arrays;
import java.util.Optional;

import nl.loc.data.event.Category;

/** Closed tag list. Gemini may only return {@link #geminiAllowed()} slugs. */
public enum ContentTag {
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
    NATURE("nature");

    private final String slug;

    ContentTag(String slug) {
        this.slug = slug;
    }

    public String slug() {
        return slug;
    }

    public boolean geminiAllowed() {
        return switch (this) {
            case ARTS, SPORTS, BUSINESS, TECHNOLOGY, LEARNING, NATURE -> false;
            default -> true;
        };
    }

    /** One honest tag from a category we already assigned. Other has none. */
    public static Optional<ContentTag> fromCategory(Category category) {
        if (category == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(switch (category) {
            case MUSIC_AND_NIGHTLIFE -> LIVE_MUSIC;
            case ARTS_AND_CULTURE -> ARTS;
            case SPORTS -> ContentTag.SPORTS;
            case BUSINESS_AND_CAREERS -> BUSINESS;
            case TECHNOLOGY_AND_SCIENCE -> TECHNOLOGY;
            case LEARNING_AND_SKILLS -> LEARNING;
            case NATURE_AND_SUSTAINABILITY -> NATURE;
            case OTHER -> null;
        });
    }

    public static Optional<ContentTag> fromSlug(String slug) {
        if (slug == null || slug.isBlank()) {
            return Optional.empty();
        }
        String normalized = slug.strip().toLowerCase();
        return Arrays.stream(values())
                .filter(tag -> tag.slug.equals(normalized))
                .findFirst();
    }

    public static String geminiSlugList() {
        return Arrays.stream(values())
                .filter(ContentTag::geminiAllowed)
                .map(ContentTag::slug)
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
    }
}
