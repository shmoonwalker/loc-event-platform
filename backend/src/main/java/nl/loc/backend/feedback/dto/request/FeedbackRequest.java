package nl.loc.backend.feedback.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "Website feedback. A signed-in member is taken from the access token, never from this body.")
public record FeedbackRequest(
        @NotBlank(message = "Please write a message")
        @Size(max = 2000, message = "Message must not exceed 2000 characters")
        @Schema(description = "Plain text. Stored trimmed, from 1 to 2000 characters.",
                example = "The map is hard to use on a phone.",
                maxLength = 2000, requiredMode = Schema.RequiredMode.REQUIRED)
        String message,

        @Size(max = 254, message = "Email must not exceed 254 characters")
        @Email(message = "Please provide a valid email address")
        @Schema(description = "Optional address to reply to. Stored trimmed; a blank value is stored as null.",
                example = "anouk.devries@example.com", maxLength = 254)
        String contactEmail) {

    public FeedbackRequest {
        if (message != null) {
            message = message.trim();
        }
        if (contactEmail != null) {
            contactEmail = contactEmail.trim();
            if (contactEmail.isEmpty()) {
                contactEmail = null;
            }
        }
    }
}
