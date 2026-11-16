package nl.loc.backend.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Why an admin is hiding an event.")
public record HideEventRequest(
        @NotBlank(message = "Please give a reason")
        @Size(max = 1000, message = "Reason must not exceed 1000 characters")
        @Schema(description = "Stored trimmed. Required, at most 1000 characters.",
                example = "Duplicate of another listing", maxLength = 1000,
                requiredMode = Schema.RequiredMode.REQUIRED)
        String reason) {
}
