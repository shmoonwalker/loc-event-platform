package nl.loc.backend.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Why an admin removes a comment")
public record RemoveCommentRequest(
        @NotBlank(message = "Please give a reason")
        @Size(max = 1000, message = "Reason must not exceed 1000 characters")
        @Schema(description = "Stored trimmed. Kept with the comment for moderation.",
                example = "Spam", maxLength = 1000, requiredMode = Schema.RequiredMode.REQUIRED)
        String reason) {
}
