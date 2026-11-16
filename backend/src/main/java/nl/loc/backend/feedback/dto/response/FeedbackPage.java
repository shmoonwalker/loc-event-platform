package nl.loc.backend.feedback.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "One page of website feedback. Open items come first, then the newest.")
public record FeedbackPage(
        @Schema(example = "0", minimum = "0", requiredMode = Schema.RequiredMode.REQUIRED) int page,
        @Schema(example = "20", maximum = "20", requiredMode = Schema.RequiredMode.REQUIRED) int size,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long totalElements,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int totalPages,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<FeedbackResponse> items) {
}
