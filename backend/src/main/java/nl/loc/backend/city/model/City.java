package nl.loc.backend.city.model;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "City slug and display name from published events.")
public record City(
        @Schema(description = "City slug.", example = "amsterdam") String slug,
        @Schema(description = "Display name.", example = "Amsterdam") String name
) {

    public static final String DEFAULT_SLUG = "amsterdam";
}
