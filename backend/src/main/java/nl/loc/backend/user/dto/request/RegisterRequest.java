package nl.loc.backend.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;

@Schema(description = "The details needed to register a new user")
public record RegisterRequest(
        @NotBlank(message = "Please provide an email")
        @Size(max = 254, message = "Email must not exceed 254 characters")
        @Email(message = "Please provide a valid email address")
        @Schema(description = "Email address of the user. Stored trimmed and lower-cased.",
                example = "anouk.devries@example.com", maxLength = 254,
                requiredMode = Schema.RequiredMode.REQUIRED)
        String email,

        @NotBlank(message = "Please provide a password")
        @Size(min = 10, message = "Password must be at least 10 characters long")
        @Schema(description = "Password of the user. At least 10 characters and at most 72 bytes in UTF-8.",
                example = "correct-horse-battery", format = "password", minLength = 10,
                accessMode = Schema.AccessMode.WRITE_ONLY, requiredMode = Schema.RequiredMode.REQUIRED)
        String password,

        @NotBlank(message = "Please provide a display name")
        @Size(max = 100, message = "Display name must not exceed 100 characters")
        @Schema(description = "Name shown to other users. Stored trimmed.",
                example = "Anouk de Vries", maxLength = 100,
                requiredMode = Schema.RequiredMode.REQUIRED)
        String displayName
) {

    // BCrypt only uses the first 72 bytes, so the limit is in bytes, not characters
    @Schema(hidden = true)
    @AssertTrue(message = "Password must not exceed 72 bytes")
    public boolean isPasswordWithinByteLimit() {
        return password == null || password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }

    // records print every field; keep the password out of logs
    @Override
    public String toString() {
        return "RegisterRequest[email=" + email + ", password=***, displayName=" + displayName + "]";
    }
}
