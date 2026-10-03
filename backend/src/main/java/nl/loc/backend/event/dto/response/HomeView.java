package nl.loc.backend.event.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import nl.loc.backend.category.dto.response.CategoryCount;
import nl.loc.backend.city.model.City;
import nl.loc.backend.tag.dto.response.TagChip;

@Schema(description = "Homepage. Query city applies only to nearYou and defaults to amsterdam. No device location.")
public record HomeView(
        @Schema(description = "Resolved near-you city (slug and name). Defaults to amsterdam. "
                + "It does not filter tags, categories, tonight, this weekend, or online.")
        City nearYouCity,
        @Schema(description = "Up to 8 tags on discoverable physical events, not filtered by city, sorted A-Z by label. "
                + "Slug and label only.")
        List<TagChip> tags,
        @Schema(description = "Categories on discoverable physical events, not filtered by city, with event counts. "
                + "Empty counts are omitted.")
        List<CategoryCount> categories,
        @Schema(description = "Physical events tonight, not filtered by city. Preview size 6. "
                + "See-all is GET /api/events?when=tonight&place=physical, page 0, size at most 20.")
        EventRail tonight,
        @Schema(description = "Physical events this weekend, not filtered by city. Preview size 6. "
                + "See-all is GET /api/events?when=weekend&place=physical, page 0, size at most 20.")
        EventRail thisWeekend,
        @Schema(description = "Physical events in nearYouCity over the next 30 days. Preview size 6. "
                + "See-all is GET /api/events?city={slug}&when=upcoming&place=physical, page 0, size at most 20.")
        EventRail nearYou,
        @Schema(description = "Online events over the next 30 days. location.type ONLINE, not filtered by city. "
                + "Preview size 6.")
        EventRail online
) {
}
