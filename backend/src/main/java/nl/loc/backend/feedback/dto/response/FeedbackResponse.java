package nl.loc.backend.feedback.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import nl.loc.backend.feedback.model.Feedback;

@Schema(description = "One piece of website feedback, as stored.")
public record FeedbackResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
        @Schema(description = "Member who sent it. Absent for a visitor, or after that account is deleted.")
        UUID userId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String message,
        @Schema(description = "Address to reply to, when the sender left one.") String contactEmail,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt,
        @Schema(description = "When an admin marked it handled.") Instant handledAt,
        @Schema(description = "Admin who marked it handled.") UUID handledBy) {

    public static FeedbackResponse from(Feedback feedback) {
        return new FeedbackResponse(feedback.getId(), feedback.getUserId(), feedback.getMessage(),
                feedback.getContactEmail(), feedback.getCreatedAt(), feedback.getHandledAt(), feedback.getHandledBy());
    }
}
