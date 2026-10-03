package nl.loc.backend.category.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Shared Loc categories. {@code label} is the value stored on a published snapshot. */
@Schema(
        description = "Shared Loc category. Query and card values are slugs. The snapshot stores the label.",
        allowableValues = {
                "music-nightlife",
                "arts-culture",
                "sports",
                "business-careers",
                "technology-science",
                "learning-skills",
                "nature-sustainability",
                "other"
        })
public enum EventCategory {
    @Schema(description = "Slug music-nightlife. Label Music & Nightlife.")
    MUSIC_NIGHTLIFE("music-nightlife", "Music & Nightlife"),
    @Schema(description = "Slug arts-culture. Label Arts & Culture.")
    ARTS_CULTURE("arts-culture", "Arts & Culture"),
    @Schema(description = "Slug sports. Label Sports.")
    SPORTS("sports", "Sports"),
    @Schema(description = "Slug business-careers. Label Business & Careers.")
    BUSINESS_CAREERS("business-careers", "Business & Careers"),
    @Schema(description = "Slug technology-science. Label Technology & Science.")
    TECHNOLOGY_SCIENCE("technology-science", "Technology & Science"),
    @Schema(description = "Slug learning-skills. Label Learning & Skills.")
    LEARNING_SKILLS("learning-skills", "Learning & Skills"),
    @Schema(description = "Slug nature-sustainability. Label Nature & Sustainability.")
    NATURE_SUSTAINABILITY("nature-sustainability", "Nature & Sustainability"),
    @Schema(description = "Slug other. Label Other.")
    OTHER("other", "Other");

    private static final Map<String, EventCategory> BY_SLUG = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(EventCategory::slug, Function.identity()));

    private final String slug;
    private final String label;

    EventCategory(String slug, String label) {
        this.slug = slug;
        this.label = label;
    }

    public String slug() {
        return slug;
    }

    /** Display label; also the exact value stored in a published snapshot's categories. */
    public String label() {
        return label;
    }

    public static Optional<EventCategory> fromSlug(String slug) {
        if (slug == null || slug.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_SLUG.get(slug.strip().toLowerCase(Locale.ROOT)));
    }
}
