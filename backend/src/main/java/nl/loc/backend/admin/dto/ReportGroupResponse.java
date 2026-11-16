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
        List<UUID> reportIds) {
}
