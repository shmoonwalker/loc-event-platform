package nl.loc.backend.event.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;
import nl.loc.backend.event.model.RailMode;

@Schema(description = "Homepage highlight. Only returned when at least one event matches.")
public record EventRail(
        @Schema(description = "Rail meaning; the frontend chooses the heading from it.") RailMode mode,
        @Schema(description = "Total distinct events matching this rail.") long total,
        @Schema(description = "Up to six preview cards, ordered by start time then event id.") List<EventCard> items,
        @Schema(description = "Whether more matches exist beyond the preview.") boolean hasMore,
        @Schema(description = "GET /api/events query parameters that reproduce this rail, for See all.",
                example = "{\"when\":\"tonight\",\"place\":\"physical\"}")
        Map<String, String> filters
) {
}
