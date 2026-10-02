package nl.loc.backend.tag.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** A tag shown on the homepage. Slug and label only. */
@Schema(description = "Tag on the homepage. Slug and label only: no score, no event count, no save ranking.")
public record TagChip(
        @Schema(description = "Tag slug.", example = "jazz") String slug,
        @Schema(description = "Display label, sorted A-Z on the homepage.", example = "Jazz") String label
) {
}
