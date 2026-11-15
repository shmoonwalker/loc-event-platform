package nl.loc.backend.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "The text of a comment or reply")
public record CommentRequest(
        @NotBlank(message = "Please write a comment")
        @Size(max = 2000, message = "Comment must not exceed 2000 characters")
        @Schema(description = "Plain text. Stored trimmed.", example = "Is there parking near the venue?",
                maxLength = 2000, requiredMode = Schema.RequiredMode.REQUIRED)
        String body) {
}
