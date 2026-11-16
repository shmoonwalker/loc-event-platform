package nl.loc.backend.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "The token from the password reset link and the new password")
public record ResetPasswordRequest(
        @NotBlank(message = "Please provide a token")
        @Size(max = 64, message = "Token must not exceed 64 characters")
        @Schema(description = "The token query parameter of the reset link (43 characters, URL-safe Base64).",
                maxLength = 64, requiredMode = Schema.RequiredMode.REQUIRED)
        String token,

        @NotBlank(message = "Please provide a password")
        @Size(min = RegisterRequest.MIN_PASSWORD_LENGTH, message = "Password must be at least 10 characters long")
        @Schema(description = "New password. At least 10 characters and at most 72 bytes in UTF-8.",
                example = "correct-horse-battery", format = "password", minLength = 10,
                accessMode = Schema.AccessMode.WRITE_ONLY, requiredMode = Schema.RequiredMode.REQUIRED)
        String newPassword) {

    @Schema(hidden = true)
    @AssertTrue(message = "Password must not exceed 72 bytes")
    public boolean isNewPasswordWithinByteLimit() {
        return RegisterRequest.withinPasswordByteLimit(newPassword);
    }

    // records print every field; keep the token and password out of logs
    @Override
    public String toString() {
        return "ResetPasswordRequest[token=***, newPassword=***]";
    }
}
