package nl.loc.backend.feedback.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import nl.loc.backend.config.ratelimit.RateLimitPolicy;
import nl.loc.backend.config.ratelimit.RateLimiter;
import nl.loc.backend.feedback.dto.request.FeedbackRequest;
import nl.loc.backend.feedback.dto.response.FeedbackResponse;
import nl.loc.backend.feedback.service.FeedbackService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/feedback")
@Tag(name = "Feedback", description = "Website feedback from visitors and members.")
public class FeedbackController {

    private final FeedbackService feedbackService;
    private final RateLimiter rateLimiter;

    public FeedbackController(FeedbackService feedbackService, RateLimiter rateLimiter) {
        this.feedbackService = feedbackService;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Send website feedback",
            description = "Accepts a message from anyone. With an access token, the member id is taken from the token. "
                    + "Without a token the feedback is anonymous. A user id in the body is ignored.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "The feedback as stored."),
            @ApiResponse(
                    responseCode = "400",
                    description = "The message is missing, blank, or longer than 2000 characters, "
                            + "or the contact email is not a valid email of at most 254 characters.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(
                    responseCode = "401",
                    description = "The access token is present but invalid or expired, or the account no longer exists.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(
                    responseCode = "429",
                    description = "Too much feedback from this IP. Retry-After says how many seconds to wait.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public FeedbackResponse submit(@AuthenticationPrincipal Jwt jwt,
                                   @Valid @RequestBody FeedbackRequest request,
                                   HttpServletRequest httpRequest) {
        rateLimiter.consume(RateLimitPolicy.FEEDBACK, httpRequest.getRemoteAddr());
        UUID userId = jwt == null ? null : UUID.fromString(jwt.getSubject());
        return feedbackService.submit(userId, request.message(), request.contactEmail());
    }
}
