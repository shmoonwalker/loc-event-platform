package nl.loc.backend.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "A comment with its replies. Replies have an empty reply list.")
public record CommentResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
        @Schema(description = "Null when the comment is deleted.") CommentAuthor author,
        @Schema(description = "Plain text. Null when the comment is deleted.") String body,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean official,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean deleted,
        @Schema(description = "True when the signed-in viewer wrote it.", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean mine,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt,
        @Schema(description = "Set when the comment was edited.") Instant editedAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<CommentResponse> replies) {
}
