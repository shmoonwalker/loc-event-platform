package nl.loc.backend.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "The email address to send a new verification link to")
public record ResendVerificationRequest(
        @NotBlank(message = "Please provide an email")
        @Size(max = 254, message = "Email must not exceed 254 characters")
        @Email(message = "Please provide a valid email address")
        @Schema(description = "Email address the account was registered with.",
                example = "anouk.devries@example.com", maxLength = 254,
                requiredMode = Schema.RequiredMode.REQUIRED)
        String email) {
}
