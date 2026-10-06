package nl.loc.backend.event.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import nl.loc.backend.city.model.City;

@Schema(description = "Homepage highlight rails. The event list itself comes from GET /api/events.")
public record HomeView(
        @Schema(description = "City used for the NEAR_YOU rail; returned even when that rail is omitted.")
        City nearYouCity,
        @Schema(description = "Rails with at least one event, in order TONIGHT, WEEKEND, NEAR_YOU, ONLINE. "
                + "Empty rails are omitted, so this can be an empty list.")
        List<EventRail> rails
) {
}
