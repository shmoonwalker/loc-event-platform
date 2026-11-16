package nl.loc.backend.photo.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Who uploaded the photo. Only the display name is public.")
public record PhotoUploader(
        @Schema(example = "Anouk", requiredMode = Schema.RequiredMode.REQUIRED) String displayName) {
}
