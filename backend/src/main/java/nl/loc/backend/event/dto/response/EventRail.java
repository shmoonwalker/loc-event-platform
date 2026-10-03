package nl.loc.backend.event.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import nl.loc.backend.event.model.RailMode;

@Schema(description = "Homepage preview. Empty results are explicit: total=0, items=[], hasMore=false.")
public record EventRail(
        @Schema(description = "Section meaning. STARTING_SOON replaces empty TONIGHT; never label it Tonight.") RailMode mode,
        @Schema(description = "Total distinct events matching this section.") long total,
        @Schema(description = "Up to six preview cards, ordered by start time then event id.") List<EventCard> items,
        @Schema(description = "Whether more matches exist beyond the preview.") boolean hasMore,
        @Schema(description = "Relative API URL with the exact effective filters, starting at page zero.") String browseUrl,
        @Schema(description = "Broader collection URL: removes the city/time restrictions, preserving physical or online.")
        String broaderBrowseUrl
) {
}
