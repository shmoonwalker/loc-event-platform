package nl.loc.backend.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import nl.loc.backend.user.model.AvatarPreset;

@Schema(description = "The preset avatar a member picks")
public record ChoosePresetRequest(
        @NotNull(message = "Please provide a preset")
        @Schema(description = "One of the preset avatars.", requiredMode = Schema.RequiredMode.REQUIRED)
        AvatarPreset preset
) {
}
