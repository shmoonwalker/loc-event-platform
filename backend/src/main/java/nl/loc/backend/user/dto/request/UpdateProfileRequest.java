package nl.loc.backend.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "The profile fields a member can change")
public record UpdateProfileRequest(
        // same rules as RegisterRequest.displayName
        @NotBlank(message = "Please provide a display name")
        @Size(max = 100, message = "Display name must not exceed 100 characters")
        @Schema(description = "Name shown to other users. Stored trimmed.",
                example = "Anouk de Vries", maxLength = 100,
                requiredMode = Schema.RequiredMode.REQUIRED)
        String displayName
) {
}
