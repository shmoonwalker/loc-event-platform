package nl.loc.backend.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(description = "Open reports of one event or comment.")
public record ReportGroupResponse(
        @Schema(description = "EVENT or COMMENT.", example = "EVENT", requiredMode = Schema.RequiredMode.REQUIRED)
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
        @Schema(description = "Show id of the reported event, or of the event the comment is on. "
                + "Absent when the comment no longer exists.")
        UUID eventId,
        @Schema(description = "Title of that event, also when it is hidden or no longer public. "
                + "Absent when the event is not in the catalog.")
        String eventTitle,
        @Schema(description = "True when an admin has hidden that event, so its public page answers 404.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        boolean eventHidden,
        @Schema(description = "Text of the reported comment. Absent for an event, or when the comment was "
                + "deleted or removed.")
        String commentBody,
        @Schema(description = "Display name of the comment's author. Absent for an event, or when the "
                + "author's account was deleted.")
        String commentAuthorName) {
}
