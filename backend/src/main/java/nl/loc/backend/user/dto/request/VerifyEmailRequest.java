package nl.loc.backend.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "The token from the verification link")
public record VerifyEmailRequest(
        @NotBlank(message = "Please provide a token")
        @Size(max = 64, message = "Token must not exceed 64 characters")
        @Schema(description = "The token query parameter of the verification link "
                + "(43 characters, URL-safe Base64).",
                maxLength = 64, requiredMode = Schema.RequiredMode.REQUIRED)
        String token) {
}
