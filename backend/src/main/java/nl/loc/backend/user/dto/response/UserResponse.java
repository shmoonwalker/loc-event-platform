package nl.loc.backend.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
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
        Role role) {

    public static UserResponse from(UserAccount account) {
        return new UserResponse(account.getId(), account.getEmail(), account.getDisplayName(), account.getRole());
    }
}
