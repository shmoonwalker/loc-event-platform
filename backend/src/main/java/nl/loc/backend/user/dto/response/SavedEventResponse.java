package nl.loc.backend.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import nl.loc.backend.event.dto.response.EventCard;

@Schema(description = "An event the signed-in member saved. Withdrawn and past events stay in the list.")
public record SavedEventResponse(
        @Schema(description = "The saved event as a card.", requiredMode = Schema.RequiredMode.REQUIRED)
        EventCard event,

        @Schema(description = "When the member saved the event, UTC.", requiredMode = Schema.RequiredMode.REQUIRED)
        Instant savedAt,

        @Schema(description = "False when the event was withdrawn or is over.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        boolean available) {
}
