package nl.loc.backend.user.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import nl.loc.backend.user.dto.request.ChoosePresetRequest;
import nl.loc.backend.user.dto.request.UpdateProfileRequest;
import nl.loc.backend.user.dto.response.UserResponse;
import nl.loc.backend.user.service.ProfileService;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
@Tag(name = "Profile", description = "Display name and avatar of the signed-in member.")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
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
}
