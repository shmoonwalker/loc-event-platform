package nl.loc.backend.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import nl.loc.backend.user.dto.request.CommentRequest;
import nl.loc.backend.user.dto.response.CommentResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/comments")
@Tag(name = "Admin comments", description = "Remove comments and post official replies.")
public class AdminCommentController {

    private final AdminCommentService adminCommentService;

    public AdminCommentController(AdminCommentService adminCommentService) {
        this.adminCommentService = adminCommentService;
    }

    @DeleteMapping("/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Remove a comment",
            description = "Removes any comment or reply for everyone. The row is kept with who removed it and why. "
                    + "Open reports of this comment are resolved. Removing it again changes nothing.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "The comment is removed."),
            @ApiResponse(
                    responseCode = "400",
                    description = "The comment id is not a UUID, or the reason is missing, blank, or longer than "
                            + "1000 characters.",
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
                    description = "No comment with this id.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public void remove(@AuthenticationPrincipal Jwt jwt,
                       @Parameter(description = "Id of the comment.") @PathVariable UUID commentId,
                       @Valid @RequestBody RemoveCommentRequest request) {
        adminCommentService.remove(UUID.fromString(jwt.getSubject()), commentId, request.reason());
    }

    @PostMapping("/{commentId}/official-replies")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Reply officially",
            description = "Posts an official reply to a top-level comment as the admin of the access token. "
                    + "Replies cannot be replied to. The reply is marked official.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "The official reply as stored."),
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
                    responseCode = "403",
                    description = "The access token is not an admin.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(
                    responseCode = "404",
                    description = "The comment is unknown, deleted, or on a hidden event.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public CommentResponse officialReply(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Id of the top-level comment.") @PathVariable UUID commentId,
            @Valid @RequestBody CommentRequest request) {
        return adminCommentService.officialReply(UUID.fromString(jwt.getSubject()), commentId, request.body());
    }
}
