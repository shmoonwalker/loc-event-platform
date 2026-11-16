package nl.loc.backend.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import nl.loc.backend.user.model.Report;
import nl.loc.backend.user.model.ReportReason;
import nl.loc.backend.user.model.ReportTargetType;

@Schema(description = "A member's report. It never includes what an admin later decided.")
public record ReportResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ReportTargetType targetType,
        @Schema(description = "Show id of a reported event, or the comment id.",
                requiredMode = Schema.RequiredMode.REQUIRED) UUID targetId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ReportReason reason,
        @Schema(description = "Absent when the member left the details blank.") String details,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt) {

    public static ReportResponse of(Report report) {
        return new ReportResponse(report.getId(), report.getTargetType(), report.getTargetId(), report.getReason(),
                report.getDetails(), report.getCreatedAt());
    }
}
