package nl.loc.backend.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import nl.loc.backend.user.model.AvatarPreset;
import nl.loc.backend.user.model.Role;
import nl.loc.backend.user.model.UserAccount;

@Schema(description = "The logged-in user, without password material")
public record UserResponse(
        @Schema(description = "Id of the account.", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID id,

        @Schema(description = "Email address of the account.", example = "anouk.devries@example.com",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String email,

        @Schema(description = "Name shown to other users.", example = "Anouk de Vries",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String displayName,

        @Schema(description = "Role of the account.", requiredMode = Schema.RequiredMode.REQUIRED)
        Role role,

        @Schema(description = "Image to show for the account: the uploaded picture, or else the preset. "
                + "Presets are paths on the frontend origin.",
                example = "/avatars/sun.svg", requiredMode = Schema.RequiredMode.REQUIRED)
        String avatarUrl,

        @Schema(description = "Preset avatar of the account, shown when there is no uploaded picture.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        AvatarPreset avatarPreset) {

    public static UserResponse from(UserAccount account) {
        return new UserResponse(account.getId(), account.getEmail(), account.getDisplayName(), account.getRole(),
                account.getAvatarPreset().url(), account.getAvatarPreset());
    }
}
