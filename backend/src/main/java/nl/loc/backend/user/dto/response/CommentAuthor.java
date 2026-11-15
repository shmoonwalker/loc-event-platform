package nl.loc.backend.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Who wrote a comment. Null on the comment when the account was deleted.")
public record CommentAuthor(
        @Schema(description = "Display name of the author.", requiredMode = Schema.RequiredMode.REQUIRED)
        String displayName) {
}
