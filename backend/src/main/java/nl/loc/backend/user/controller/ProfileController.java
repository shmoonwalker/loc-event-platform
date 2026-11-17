package nl.loc.backend.user.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.UUID;
import nl.loc.backend.config.ratelimit.RateLimitPolicy;
import nl.loc.backend.config.ratelimit.RateLimiter;
import nl.loc.backend.user.dto.request.ChoosePresetRequest;
import nl.loc.backend.user.dto.request.UpdateProfileRequest;
import nl.loc.backend.user.dto.response.UserResponse;
import nl.loc.backend.user.service.ProfileService;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/me")
@Tag(name = "Profile", description = "Display name and avatar of the signed-in member.")
public class ProfileController {

    private static final long MAX_AVATAR_BYTES = 5L * 1024 * 1024;

    private final ProfileService profileService;
    private final RateLimiter rateLimiter;

    public ProfileController(ProfileService profileService, RateLimiter rateLimiter) {
        this.profileService = profileService;
        this.rateLimiter = rateLimiter;
    }

    @PatchMapping("/profile")
    @Operation(
            summary = "Change display name",
            description = "Changes the display name of the member of the access token in the Authorization header, "
                    + "with the same rules as registration, and returns the updated account.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The updated account."),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid input or malformed JSON.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(
                    responseCode = "401",
                    description = "Missing, invalid or expired access token, or the account no longer exists.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public UserResponse updateProfile(@AuthenticationPrincipal Jwt jwt,
                                      @Valid @RequestBody UpdateProfileRequest request) {
        return profileService.changeDisplayName(UUID.fromString(jwt.getSubject()), request.displayName());
    }

    @PutMapping("/avatar/preset")
    @Operation(
            summary = "Pick a preset avatar",
            description = "Sets the preset avatar of the member of the access token in the Authorization header "
                    + "and returns the updated account.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The updated account."),
            @ApiResponse(
                    responseCode = "400",
                    description = "Missing or unknown preset, or malformed JSON.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(
                    responseCode = "401",
                    description = "Missing, invalid or expired access token, or the account no longer exists.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public UserResponse choosePreset(@AuthenticationPrincipal Jwt jwt,
                                     @Valid @RequestBody ChoosePresetRequest request) {
        return profileService.choosePreset(UUID.fromString(jwt.getSubject()), request.preset());
    }

    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Upload a profile picture",
            description = "Stores a profile picture for the member of the access token in the Authorization header "
                    + "and returns the updated account. Accepts JPEG, PNG or WebP up to 5 MB, recognised by content, "
                    + "not by file name. The picture is cropped to a 512x512 square, saved as JPEG and stripped of "
                    + "all metadata such as location and camera data. It replaces an earlier upload.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The updated account, with the new avatarUrl."),
            @ApiResponse(
                    responseCode = "400",
                    description = "No file part, or the file is not a readable JPEG, PNG or WebP image.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(
                    responseCode = "401",
                    description = "Missing, invalid or expired access token, or the account no longer exists.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(
                    responseCode = "413",
                    description = "The file is larger than 5 MB.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(
                    responseCode = "429",
                    description = "Too many uploads by this member. Retry-After says how many seconds to wait.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(
                    responseCode = "503",
                    description = "Picture uploads are not available on this server.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public UserResponse uploadAvatar(@AuthenticationPrincipal Jwt jwt,
                                     @Parameter(description = "The image file.") @RequestParam MultipartFile file)
            throws IOException {
        if (file.getSize() > MAX_AVATAR_BYTES) {
            throw new MaxUploadSizeExceededException(MAX_AVATAR_BYTES);
        }
        // before decoding, so blocked requests cost almost nothing
        rateLimiter.consume(RateLimitPolicy.AVATAR_UPLOAD, jwt.getSubject());
        return profileService.uploadAvatar(UUID.fromString(jwt.getSubject()), file.getBytes());
    }

    @DeleteMapping("/avatar")
    @Operation(
            summary = "Remove the profile picture",
            description = "Deletes the uploaded picture of the member of the access token in the Authorization header, "
                    + "so the preset shows again, and returns the updated account. Without an upload nothing changes.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The updated account, with the preset as avatarUrl."),
            @ApiResponse(
                    responseCode = "401",
                    description = "Missing, invalid or expired access token, or the account no longer exists.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public UserResponse removeAvatar(@AuthenticationPrincipal Jwt jwt) {
        return profileService.removeUploadedAvatar(UUID.fromString(jwt.getSubject()));
    }
}
