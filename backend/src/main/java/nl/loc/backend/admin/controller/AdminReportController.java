package nl.loc.backend.admin.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import nl.loc.backend.admin.dto.ReportGroupResponse;
import nl.loc.backend.admin.service.AdminReportService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin moderation", description = "The queue of open member reports. Admin only.")
public class AdminReportController {

    private final AdminReportService adminReportService;

    public AdminReportController(AdminReportService adminReportService) {
        this.adminReportService = adminReportService;
    }

    @GetMapping("/reports")
    @Operation(
            summary = "Open reports",
            description = "Open reports grouped by the reported event or comment. Each group has the count, "
                    + "the distinct reasons, and the report ids. Resolved reports are left out.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The open report groups. Empty when nothing is open."),
            @ApiResponse(
                    responseCode = "401",
                    description = "Missing, invalid or expired access token.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(
                    responseCode = "403",
                    description = "The access token is not an admin.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public List<ReportGroupResponse> reports() {
        return adminReportService.openReports();
    }

    @PostMapping("/reports/{reportId}/dismiss")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Dismiss a report",
            description = "Closes one open report without hiding or otherwise changing the reported item. "
                    + "The resolution is DISMISSED.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "The report is dismissed."),
            @ApiResponse(
                    responseCode = "400",
                    description = "The report id is not a UUID.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(
                    responseCode = "401",
                    description = "Missing, invalid or expired access token, or the account no longer exists.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(
                    responseCode = "403",
                    description = "The access token is not an admin.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(
                    responseCode = "404",
                    description = "The report is unknown or already resolved.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public void dismiss(@AuthenticationPrincipal Jwt jwt,
                        @Parameter(description = "Id of an open report.") @PathVariable UUID reportId) {
        adminReportService.dismiss(UUID.fromString(jwt.getSubject()), reportId);
    }
}
