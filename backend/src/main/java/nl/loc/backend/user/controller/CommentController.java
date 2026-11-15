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
import nl.loc.backend.event.controller.BrowseCriteriaParser;
import nl.loc.backend.user.dto.request.CommentRequest;
import nl.loc.backend.user.dto.response.CommentPage;
import nl.loc.backend.user.dto.response.CommentResponse;
import nl.loc.backend.user.service.CommentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "Comments", description = "Comments on events, with one level of replies.")
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @GetMapping("/events/{eventId}/comments")
    @Operation(
            summary = "Event comments",
            description = "One page of the event's top-level comments, newest first, each with its replies oldest "
                    + "first (page from 0, size 1-20, default 20). Public; with an access token, mine marks the "
                    + "viewer's own comments. A deleted comment with replies has no author or body.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "One page of comments."),
            @ApiResponse(
                    responseCode = "400",
                    description = "The event id is not a UUID, page is negative, or size is not between 1 and 20.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(
                    responseCode = "404",
                    description = "The event is unknown or hidden.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public CommentPage comments(@AuthenticationPrincipal Jwt jwt,
                                @Parameter(description = "Id of the event.") @PathVariable UUID eventId,
                                @Parameter(description = "Zero-based page index. Defaults to 0.")
                                @RequestParam(required = false) Integer page,
                                @Parameter(description = "Page size from 1 to 20. Defaults to 20.")
                                @RequestParam(required = false) Integer size) {
        return commentService.comments(eventId, jwt == null ? null : UUID.fromString(jwt.getSubject()),
                BrowseCriteriaParser.page(page), BrowseCriteriaParser.size(size));
    }

    @PostMapping("/events/{eventId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Comment on an event",
            description = "Posts a top-level comment as the member of the access token in the Authorization header.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "The comment as stored."),
            @ApiResponse(
                    responseCode = "400",
                    description = "The event id is not a UUID, or the body is empty or longer than 2000 characters.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(
                    responseCode = "401",
                    description = "Missing, invalid or expired access token, or the account no longer exists.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(
                    responseCode = "404",
                    description = "The event is unknown or hidden.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(
                    responseCode = "429",
                    description = "Too many comments from this member. Retry-After says how many seconds to wait.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public CommentResponse comment(@AuthenticationPrincipal Jwt jwt,
                                   @Parameter(description = "Id of the event.") @PathVariable UUID eventId,
                                   @Valid @RequestBody CommentRequest request) {
        return commentService.createComment(UUID.fromString(jwt.getSubject()), eventId, request.body());
    }

    @PostMapping("/comments/{commentId}/replies")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Reply to a comment",
            description = "Posts a reply to a top-level comment as the member of the access token in the "
                    + "Authorization header. Replies cannot be replied to.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "The reply as stored."),
            @ApiResponse(
                    responseCode = "400",
                    description = "The comment id is not a UUID, the body is empty or longer than 2000 characters, "
                            + "or the comment is itself a reply.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(
                    responseCode = "401",
                    description = "Missing, invalid or expired access token, or the account no longer exists.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(
                    responseCode = "404",
                    description = "The comment is unknown, deleted, or on a hidden event.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(
                    responseCode = "429",
                    description = "Too many comments from this member. Retry-After says how many seconds to wait.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public CommentResponse reply(@AuthenticationPrincipal Jwt jwt,
                                 @Parameter(description = "Id of the top-level comment.") @PathVariable UUID commentId,
                                 @Valid @RequestBody CommentRequest request) {
        return commentService.reply(UUID.fromString(jwt.getSubject()), commentId, request.body());
    }

    @PatchMapping("/me/comments/{commentId}")
    @Operation(
            summary = "Edit my comment",
            description = "Replaces the text of a comment or reply of the member of the access token. "
                    + "The comment then shows as edited. Replies are not included in the response.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The comment as stored."),
            @ApiResponse(
                    responseCode = "400",
                    description = "The comment id is not a UUID, or the body is empty or longer than 2000 characters.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(
                    responseCode = "401",
                    description = "Missing, invalid or expired access token, or the account no longer exists.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(
                    responseCode = "404",
                    description = "No comment of this member with this id, or it is deleted or on a hidden event.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public CommentResponse edit(@AuthenticationPrincipal Jwt jwt,
                                @Parameter(description = "Id of the comment.") @PathVariable UUID commentId,
                                @Valid @RequestBody CommentRequest request) {
        return commentService.editComment(UUID.fromString(jwt.getSubject()), commentId, request.body());
    }

    @DeleteMapping("/me/comments/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Delete my comment",
            description = "Deletes a comment or reply of the member of the access token for everyone. "
                    + "A deleted comment with replies shows without author or text; one without replies disappears. "
                    + "Deleting it again changes nothing.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "The comment is deleted."),
            @ApiResponse(
                    responseCode = "400",
                    description = "The comment id is not a UUID.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(
                    responseCode = "401",
                    description = "Missing, invalid or expired access token.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(
                    responseCode = "404",
                    description = "No comment of this member with this id.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public void delete(@AuthenticationPrincipal Jwt jwt,
                       @Parameter(description = "Id of the comment.") @PathVariable UUID commentId) {
        commentService.deleteComment(UUID.fromString(jwt.getSubject()), commentId);
    }
}
