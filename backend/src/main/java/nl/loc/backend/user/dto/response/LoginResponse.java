package nl.loc.backend.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Duration;

@Schema(description = "The result of a successful login")
public record LoginResponse(
        @Schema(description = "Short-lived JWT; send it as 'Authorization: Bearer <token>'.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String accessToken,

        @Schema(description = "How to send the access token.", example = "Bearer",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String tokenType,

        @Schema(description = "Seconds until the access token expires.", example = "900",
                requiredMode = Schema.RequiredMode.REQUIRED)
        long expiresIn) {

    public LoginResponse(String accessToken, Duration expiresIn) {
        this(accessToken, "Bearer", expiresIn.toSeconds());
    }
}
