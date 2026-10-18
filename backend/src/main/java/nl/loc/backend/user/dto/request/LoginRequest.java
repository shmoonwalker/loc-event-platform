package nl.loc.backend.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "The credentials needed to log in")
public record LoginRequest(
        @NotBlank(message = "Please provide an email")
        @Size(max = 254, message = "Email must not exceed 254 characters")
        @Schema(description = "Email address the account was registered with.",
                example = "anouk.devries@example.com", maxLength = 254,
                requiredMode = Schema.RequiredMode.REQUIRED)
        String email,

        @NotBlank(message = "Please provide a password")
        @Schema(description = "Password of the account.", example = "correct-horse-battery",
                format = "password", accessMode = Schema.AccessMode.WRITE_ONLY,
                requiredMode = Schema.RequiredMode.REQUIRED)
        String password) {

    // records print every field; keep the password out of logs
    @Override
    public String toString() {
        return "LoginRequest[email=" + email + ", password=***]";
    }
}