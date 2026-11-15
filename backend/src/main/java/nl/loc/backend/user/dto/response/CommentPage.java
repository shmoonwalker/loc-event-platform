package nl.loc.backend.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "One page of an event's top-level comments, newest first, each with its replies oldest first.")
public record CommentPage(
        @Schema(example = "0", minimum = "0", requiredMode = Schema.RequiredMode.REQUIRED) int page,
        @Schema(example = "20", maximum = "20", requiredMode = Schema.RequiredMode.REQUIRED) int size,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long totalElements,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int totalPages,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<CommentResponse> items) {
}
