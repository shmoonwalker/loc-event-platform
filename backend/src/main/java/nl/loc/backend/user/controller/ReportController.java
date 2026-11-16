package nl.loc.backend.user.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import nl.loc.backend.user.dto.request.ReportRequest;
import nl.loc.backend.user.dto.response.ReportResponse;
import nl.loc.backend.user.service.ReportService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "Reports", description = "A signed-in member reports an event or a comment.")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @PostMapping("/events/{eventId}/reports")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Report an event",
            description = "Reports the event as the member of the access token. The id may be a show id or an older "
                    + "event id. Reporting the same event again returns the open report and adds no second one. "
                    + "The answer never includes what an admin decided.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "The open report, including when it was already stored."),
            @ApiResponse(
                    responseCode = "400",
                    description = "The event id is not a UUID, the reason is missing or unknown, or details are "
                            + "longer than 1000 characters.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(
                    responseCode = "401",
                    description = "Missing, invalid or expired access token, or the account no longer exists.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(
                    responseCode = "404",
                    description = "No published event with this id.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(
                    responseCode = "429",
                    description = "Too many reports from this member. Retry-After says how many seconds to wait.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ReportResponse reportEvent(@AuthenticationPrincipal Jwt jwt,
                                      @Parameter(description = "Show id, or an older event id.")
                                      @PathVariable UUID eventId,
                                      @Valid @RequestBody ReportRequest request) {
        return reportService.reportEvent(UUID.fromString(jwt.getSubject()), eventId, request.reason(),
                request.details());
    }

    @PostMapping("/comments/{commentId}/reports")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Report a comment",
            description = "Reports the comment as the member of the access token. Reporting the same comment again "
                    + "returns the open report and adds no second one. The answer never includes what an admin decided.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "The open report, including when it was already stored."),
            @ApiResponse(
                    responseCode = "400",
                    description = "The comment id is not a UUID, the reason is missing or unknown, or details are "
                            + "longer than 1000 characters.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(
                    responseCode = "401",
                    description = "Missing, invalid or expired access token, or the account no longer exists.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(
                    responseCode = "404",
                    description = "The comment is unknown or deleted.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(
                    responseCode = "429",
                    description = "Too many reports from this member. Retry-After says how many seconds to wait.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ReportResponse reportComment(@AuthenticationPrincipal Jwt jwt,
                                        @Parameter(description = "Id of the comment.") @PathVariable UUID commentId,
                                        @Valid @RequestBody ReportRequest request) {
        return reportService.reportComment(UUID.fromString(jwt.getSubject()), commentId, request.reason(),
                request.details());
    }
}
