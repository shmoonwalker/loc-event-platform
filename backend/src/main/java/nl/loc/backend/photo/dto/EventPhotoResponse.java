package nl.loc.backend.photo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import nl.loc.backend.photo.model.PhotoStatus;

@Schema(description = "One event photo. Pending and rejected photos never carry a public storage URL.")
public record EventPhotoResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
        @Schema(description = "Show id.", requiredMode = Schema.RequiredMode.REQUIRED) UUID eventId,
        @Schema(description = "Title of the show. May be absent on the public list.") String eventTitle,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) PhotoStatus status,
        @Schema(description = "JPEG for a card. A relative file route, or a public URL once approved.",
                requiredMode = Schema.RequiredMode.REQUIRED) String cardUrl,
        @Schema(description = "JPEG for the full view. A relative file route, or a public URL once approved.",
                requiredMode = Schema.RequiredMode.REQUIRED) String fullUrl,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) PhotoUploader uploader) {
}
