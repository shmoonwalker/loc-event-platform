package nl.loc.backend.city.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Arrays;
import java.util.stream.Collectors;

@Schema(description = "City slug and display name from published events.")
public record City(
        @Schema(description = "City slug.", example = "amsterdam") String slug,
        @Schema(description = "Display name.", example = "Amsterdam") String name
) {

    public static final String DEFAULT_SLUG = "amsterdam";

    /** Fallback when no published event names this city: den-haag becomes Den Haag. */
    public static City fromSlug(String slug) {
        String name = Arrays.stream(slug.split("-"))
                .filter(word -> !word.isEmpty())
                .map(word -> Character.toUpperCase(word.charAt(0)) + word.substring(1))
                .collect(Collectors.joining(" "));
        return new City(slug, name);
    }
}
