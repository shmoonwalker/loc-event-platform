package nl.loc.backend.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "A show an admin has hidden from public discovery.")
public record HiddenEventResponse(
        @Schema(description = "Show id (loc_group_id).", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID eventId,
        @Schema(description = "Admin who hid the event.", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID hiddenBy,
        @Schema(description = "Display name of the admin who hid the event.", requiredMode = Schema.RequiredMode.REQUIRED)
        String hiddenByName,
        @Schema(description = "When the event was hidden, UTC.", requiredMode = Schema.RequiredMode.REQUIRED)
        Instant hiddenAt,
        @Schema(description = "Why the event was hidden.", requiredMode = Schema.RequiredMode.REQUIRED)
        String reason) {
}
