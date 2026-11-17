package nl.loc.backend.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(description = "Open reports of one event, comment, or photo.")
public record ReportGroupResponse(
        @Schema(description = "EVENT, COMMENT, or PHOTO.", example = "EVENT", requiredMode = Schema.RequiredMode.REQUIRED)
        String targetType,
        @Schema(description = "Show id for an event, or the comment id.", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID targetId,
        @Schema(description = "Number of open reports of this item.", example = "2",
                requiredMode = Schema.RequiredMode.REQUIRED)
        long openCount,
        @Schema(description = "Distinct reasons, alphabetical.", requiredMode = Schema.RequiredMode.REQUIRED)
        List<String> reasons,
        @Schema(description = "Open report ids, oldest first. Dismiss one of these to close it.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        List<UUID> reportIds,
        @Schema(description = "Show the item belongs to. An event report filed on an older event id gives its show id. "
                + "Absent for a comment that no longer exists.")
        UUID eventId,
        @Schema(description = "Event title. Absent when the show is no longer published.")
        String eventTitle,
        @Schema(description = "Text of the reported comment. Absent for an event and after the comment was removed.")
        String commentBody,
        @Schema(description = "Display name of the comment's author. Absent for an event and after that account "
                + "is deleted.")
        String commentAuthorName) {
}
