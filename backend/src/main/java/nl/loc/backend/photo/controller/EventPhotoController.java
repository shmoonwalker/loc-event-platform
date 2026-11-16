package nl.loc.backend.photo.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import nl.loc.backend.config.ratelimit.RateLimitPolicy;
import nl.loc.backend.config.ratelimit.RateLimiter;
import nl.loc.backend.photo.dto.EventPhotoResponse;
import nl.loc.backend.photo.model.PhotoVariant;
import nl.loc.backend.photo.service.EventPhotoImages;
import nl.loc.backend.photo.service.EventPhotoService;
import nl.loc.backend.photo.service.EventPhotoService.PhotoFile;
import nl.loc.backend.user.service.InvalidImageException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
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
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
@Tag(name = "Event photos", description = "A member uploads photos of an event. An admin approves them before they are public.")
public class EventPhotoController {

    private final EventPhotoService eventPhotoService;
    private final RateLimiter rateLimiter;

    public EventPhotoController(EventPhotoService eventPhotoService, RateLimiter rateLimiter) {
        this.eventPhotoService = eventPhotoService;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping(value = "/events/{eventId}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Upload a photo",
            description = "Stores a photo of the event for the member of the access token. The photo stays PENDING "
                    + "until an admin approves it. Accepts JPEG, PNG or WebP up to 10 MB, recognised by content. "
                    + "The file is re-encoded as two JPEGs, a card and a full view, and the original is discarded, "
                    + "including location data.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "The stored photo, status PENDING."),
            @ApiResponse(responseCode = "400", description = "No file, or the file is not a readable JPEG, PNG or WebP.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "Missing, invalid or expired access token.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "The event is unknown or withdrawn.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "This member already has 10 pending or approved photos of the event.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "413", description = "The file is larger than 10 MB.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "429", description = "Too many uploads by this member. Retry-After says how many seconds to wait.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "503", description = "Photo uploads are not available on this server.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public EventPhotoResponse upload(@AuthenticationPrincipal Jwt jwt,
                                     @Parameter(description = "Show id, or an older event id.") @PathVariable UUID eventId,
                                     @Parameter(description = "The image file.") @RequestParam MultipartFile file)
            throws IOException {
        if (file.isEmpty()) {
            throw new InvalidImageException("Only JPEG, PNG and WebP images are accepted");
        }
        if (file.getSize() > EventPhotoImages.MAX_BYTES) {
            throw new MaxUploadSizeExceededException(EventPhotoImages.MAX_BYTES);
        }
        rateLimiter.consume(RateLimitPolicy.PHOTO_UPLOAD, jwt.getSubject());
        return eventPhotoService.upload(UUID.fromString(jwt.getSubject()), eventId, file.getBytes());
    }

    @GetMapping("/events/{eventId}/photos")
    @Operation(summary = "Approved photos", description = "Public. Approved photos of the event, newest first.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Approved photos. Empty when none are approved."),
            @ApiResponse(responseCode = "404", description = "The event is unknown or withdrawn.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public List<EventPhotoResponse> approved(
            @Parameter(description = "Show id, or an older event id.") @PathVariable UUID eventId) {
        return eventPhotoService.approved(eventId);
    }

    @GetMapping("/me/events/{eventId}/photos")
    @Operation(summary = "Your photos", description = "The caller's photos of the event, every status, newest first.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The caller's photos. Empty when they uploaded none."),
            @ApiResponse(responseCode = "401", description = "Missing, invalid or expired access token.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "The event is unknown or withdrawn.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public List<EventPhotoResponse> own(@AuthenticationPrincipal Jwt jwt,
                                        @Parameter(description = "Show id, or an older event id.") @PathVariable UUID eventId) {
        return eventPhotoService.own(UUID.fromString(jwt.getSubject()), eventId);
    }

    @DeleteMapping("/events/{eventId}/photos/{photoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete your photo", description = "The owner deletes their photo and its stored files.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "The photo and its stored files are gone."),
            @ApiResponse(responseCode = "401", description = "Missing, invalid or expired access token.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "The photo belongs to someone else.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "The event or photo is unknown, or the event is withdrawn.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public void delete(@AuthenticationPrincipal Jwt jwt,
                       @Parameter(description = "Show id, or an older event id.") @PathVariable UUID eventId,
                       @Parameter(description = "Id of the photo.") @PathVariable UUID photoId) {
        eventPhotoService.delete(UUID.fromString(jwt.getSubject()), eventId, photoId);
    }

    @GetMapping(value = "/events/{eventId}/photos/{photoId}/file", produces = MediaType.IMAGE_JPEG_VALUE)
    @Operation(
            summary = "Photo file",
            description = "Streams the card or full JPEG. An approved photo is public. A pending or rejected photo "
                    + "is only for the member who uploaded it, or an admin.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The JPEG."),
            @ApiResponse(responseCode = "400", description = "The variant is missing or not card or full.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "The photo is not approved and no access token was sent.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "The photo is not approved and the caller is not the owner or an admin.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "The event or photo is unknown, or the event is withdrawn.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<byte[]> file(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Show id, or an older event id.") @PathVariable UUID eventId,
            @Parameter(description = "Id of the photo.") @PathVariable UUID photoId,
            @Parameter(description = "card or full.") @RequestParam String variant) {
        PhotoFile photo = eventPhotoService.file(eventId, photoId, variant(variant), jwt);
        CacheControl cache = photo.approved()
                ? CacheControl.maxAge(java.time.Duration.ofDays(365)).cachePublic().immutable()
                : CacheControl.noStore();
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).cacheControl(cache).body(photo.bytes());
    }

    private static PhotoVariant variant(String value) {
        PhotoVariant variant = PhotoVariant.fromParam(value);
        if (variant == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Variant must be card or full.");
        }
        return variant;
    }
}
