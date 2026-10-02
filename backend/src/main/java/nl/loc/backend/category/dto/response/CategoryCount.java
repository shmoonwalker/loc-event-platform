package nl.loc.backend.category.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Homepage category and how many discoverable events carry it. Not limited to near you.")
public record CategoryCount(
        @Schema(description = "Category slug.", example = "music-nightlife") String slug,
        @Schema(description = "Category label.", example = "Music & Nightlife") String label,
        @Schema(description = "Discoverable events with this category, not filtered by the near-you city.", example = "12")
        long eventCount
) {
}
