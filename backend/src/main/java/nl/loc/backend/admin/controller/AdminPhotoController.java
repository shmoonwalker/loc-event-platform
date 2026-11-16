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
import nl.loc.backend.photo.dto.EventPhotoResponse;
import nl.loc.backend.photo.model.PhotoStatus;
import nl.loc.backend.photo.service.EventPhotoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/photos")
@Tag(name = "Admin photos", description = "The queue of event photos. Admin only.")
public class AdminPhotoController {

    private final EventPhotoService eventPhotoService;

    public AdminPhotoController(EventPhotoService eventPhotoService) {
        this.eventPhotoService = eventPhotoService;
    }

    @GetMapping
    @Operation(
            summary = "Photos by status",
            description = "Up to 50 photos of one status, newest first. Defaults to PENDING.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The matching photos. Empty when there are none."),
            @ApiResponse(responseCode = "400", description = "The status is not PENDING, APPROVED or REJECTED.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "Missing, invalid or expired access token.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "The access token is not an admin.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public List<EventPhotoResponse> list(
            @Parameter(description = "PENDING, APPROVED or REJECTED. Defaults to PENDING.")
            @RequestParam(defaultValue = "PENDING") PhotoStatus status) {
        return eventPhotoService.adminList(status);
    }

    @GetMapping("/{photoId}")
    @Operation(summary = "One photo", description = "The photo for the review page, whatever its status.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The photo."),
            @ApiResponse(responseCode = "401", description = "Missing, invalid or expired access token.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "The access token is not an admin.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "No photo with this id.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public EventPhotoResponse photo(@Parameter(description = "Id of the photo.") @PathVariable UUID photoId) {
        return eventPhotoService.adminGet(photoId);
    }

    @PostMapping("/{photoId}/approve")
    @Operation(summary = "Approve a photo", description = "Marks the photo APPROVED so it can appear in the public album.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The approved photo."),
            @ApiResponse(responseCode = "401", description = "Missing, invalid or expired access token.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "The access token is not an admin.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "No photo with this id.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public EventPhotoResponse approve(@AuthenticationPrincipal Jwt jwt,
                                      @Parameter(description = "Id of the photo.") @PathVariable UUID photoId) {
        return eventPhotoService.approve(UUID.fromString(jwt.getSubject()), photoId);
    }

    @PostMapping("/{photoId}/reject")
    @Operation(summary = "Reject a photo", description = "Marks the photo REJECTED. It stays out of the public album.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The rejected photo."),
            @ApiResponse(responseCode = "401", description = "Missing, invalid or expired access token.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "The access token is not an admin.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "No photo with this id.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public EventPhotoResponse reject(@AuthenticationPrincipal Jwt jwt,
                                     @Parameter(description = "Id of the photo.") @PathVariable UUID photoId) {
        return eventPhotoService.reject(UUID.fromString(jwt.getSubject()), photoId);
    }

    @DeleteMapping("/{photoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Remove a photo",
            description = "Deletes the photo and its stored files, whatever its status, and resolves its open reports.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "The photo, its stored files, and its open reports are gone."),
            @ApiResponse(responseCode = "401", description = "Missing, invalid or expired access token.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "The access token is not an admin.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "No photo with this id.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public void delete(@AuthenticationPrincipal Jwt jwt,
                       @Parameter(description = "Id of the photo.") @PathVariable UUID photoId) {
        eventPhotoService.adminDelete(UUID.fromString(jwt.getSubject()), photoId);
    }
}
