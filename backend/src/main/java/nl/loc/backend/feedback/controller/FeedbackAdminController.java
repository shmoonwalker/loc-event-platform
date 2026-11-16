package nl.loc.backend.feedback.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import nl.loc.backend.event.controller.BrowseCriteriaParser;
import nl.loc.backend.feedback.dto.response.FeedbackPage;
import nl.loc.backend.feedback.dto.response.FeedbackResponse;
import nl.loc.backend.feedback.service.FeedbackService;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/feedback")
@Tag(name = "Feedback admin", description = "Read website feedback and mark it handled. Admin only.")
public class FeedbackAdminController {

    private final FeedbackService feedbackService;

    public FeedbackAdminController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @GetMapping
    @Operation(
            summary = "List website feedback",
            description = "One page of feedback for an admin. Open items (not handled yet) come first, then the "
                    + "newest (page from 0, size 1-20, default 20, same as /api/events).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "One page of feedback."),
            @ApiResponse(
                    responseCode = "400",
                    description = "page is negative, or size is not between 1 and 20.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(
                    responseCode = "401",
                    description = "Missing, invalid or expired access token.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(
                    responseCode = "403",
                    description = "The access token is not an admin.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public FeedbackPage list(@Parameter(description = "Zero-based page index. Defaults to 0.")
                             @RequestParam(required = false) Integer page,
                             @Parameter(description = "Page size from 1 to 20. Defaults to 20.")
                             @RequestParam(required = false) Integer size) {
        return feedbackService.list(BrowseCriteriaParser.page(page), BrowseCriteriaParser.size(size));
    }

    @PostMapping("/{feedbackId}/handled")
    @Operation(
            summary = "Mark feedback handled",
            description = "Records the admin of the access token and the time. Marking feedback that is already "
                    + "handled changes nothing and still succeeds.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The feedback, handled."),
            @ApiResponse(
                    responseCode = "400",
                    description = "The feedback id is not a UUID.",
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
                    description = "No feedback with this id.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public FeedbackResponse markHandled(@AuthenticationPrincipal Jwt jwt,
                                        @Parameter(description = "Id of the feedback.") @PathVariable UUID feedbackId) {
        return feedbackService.markHandled(UUID.fromString(jwt.getSubject()), feedbackId);
    }
}
