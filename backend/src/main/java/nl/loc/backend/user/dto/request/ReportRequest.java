package nl.loc.backend.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import nl.loc.backend.user.model.ReportReason;

@Schema(description = "Why a member is reporting an event or a comment")
public record ReportRequest(
        @NotNull(message = "Please choose a reason")
        @Schema(description = "One of WRONG_INFORMATION, SPAM, INAPPROPRIATE, OTHER.",
                example = "SPAM", requiredMode = Schema.RequiredMode.REQUIRED)
        ReportReason reason,
        @Size(max = 1000, message = "Details must not exceed 1000 characters")
        @Schema(description = "Optional text. Blank text is stored as nothing.", maxLength = 1000,
                example = "The date on this page is last year's.")
        String details) {
}
