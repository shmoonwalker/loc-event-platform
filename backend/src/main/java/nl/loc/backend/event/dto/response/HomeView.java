package nl.loc.backend.event.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import nl.loc.backend.city.model.City;

@Schema(description = "Discovery homepage. city scopes only nearYou; defaults to Amsterdam. Filters have a separate endpoint.")
public record HomeView(
        City nearYouCity,
        @Schema(description = "Physical evening events, or STARTING_SOON when no evening events match, across all cities.")
        EventRail tonight,
        @Schema(description = "Physical events this weekend across all cities; no fallback.") EventRail thisWeekend,
        @Schema(description = "Physical events in the selected city over 30 days; no location substitution.") EventRail nearYou,
        @Schema(description = "Online events over 30 days. The broader link also includes later events.") EventRail online,
        @Schema(description = "Relative API URL for ordinary browsing without city, date, place, or search restrictions.")
        String browseUrl
) {
}
