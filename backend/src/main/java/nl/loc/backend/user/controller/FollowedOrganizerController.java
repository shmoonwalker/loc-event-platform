package nl.loc.backend.user.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import nl.loc.backend.event.controller.BrowseCriteriaParser;
import nl.loc.backend.event.dto.response.EventPage;
import nl.loc.backend.event.dto.response.OrganizerInfo;
import nl.loc.backend.user.service.FollowedOrganizerService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me/followed-organizers")
@Tag(name = "Followed organizers", description = "Organizers the signed-in member follows.")
public class FollowedOrganizerController {

    private final FollowedOrganizerService followedOrganizerService;

    public FollowedOrganizerController(FollowedOrganizerService followedOrganizerService) {
        this.followedOrganizerService = followedOrganizerService;
    }

    @GetMapping
    @Operation(
            summary = "Followed organizers",
            description = "Organizers the member of the access token in the Authorization header follows, "
                    + "most recently followed first.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The followed organizers."),
            @ApiResponse(
                    responseCode = "401",
                    description = "Missing, invalid or expired access token.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public List<OrganizerInfo> followedOrganizers(@AuthenticationPrincipal Jwt jwt) {
        return followedOrganizerService.followedOrganizers(UUID.fromString(jwt.getSubject()));
    }

    @GetMapping("/events")
    @Operation(
            summary = "Events of followed organizers",
            description = "One page of upcoming events of the organizers the member of the access token in the "
                    + "Authorization header follows, soonest first (page from 0, size 1-20, default 20, "
                    + "same as /api/events). Withdrawn and past events are left out.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "One page of events."),
            @ApiResponse(
                    responseCode = "400",
                    description = "page is negative, or size is not between 1 and 20.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(
                    responseCode = "401",
                    description = "Missing, invalid or expired access token.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public EventPage followedEvents(@AuthenticationPrincipal Jwt jwt,
                                    @Parameter(description = "Zero-based page index. Defaults to 0.")
                                    @RequestParam(required = false) Integer page,
                                    @Parameter(description = "Page size from 1 to 20. Defaults to 20.")
                                    @RequestParam(required = false) Integer size) {
        return followedOrganizerService.followedEvents(UUID.fromString(jwt.getSubject()),
                BrowseCriteriaParser.page(page), BrowseCriteriaParser.size(size));
    }

    @PutMapping("/{organizerId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Follow an organizer",
            description = "Follows the organizer for the member of the access token in the Authorization header. "
                    + "Following an organizer that is already followed changes nothing.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "The organizer is followed."),
            @ApiResponse(
                    responseCode = "400",
                    description = "The organizer id is not a UUID.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(
                    responseCode = "401",
                    description = "Missing, invalid or expired access token, or the account no longer exists.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(
                    responseCode = "404",
                    description = "The organizer is unknown.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public void follow(@AuthenticationPrincipal Jwt jwt,
                       @Parameter(description = "Id of the organizer to follow.") @PathVariable UUID organizerId) {
        followedOrganizerService.followOrganizer(UUID.fromString(jwt.getSubject()), organizerId);
    }

    @DeleteMapping("/{organizerId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Unfollow an organizer",
            description = "Removes the organizer from the followed organizers of the member of the access token "
                    + "in the Authorization header. Unfollowing an organizer that is not followed also answers 204.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "The organizer is not followed."),
            @ApiResponse(
                    responseCode = "400",
                    description = "The organizer id is not a UUID.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(
                    responseCode = "401",
                    description = "Missing, invalid or expired access token.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public void unfollow(@AuthenticationPrincipal Jwt jwt,
                         @Parameter(description = "Id of the organizer to unfollow.") @PathVariable UUID organizerId) {
        followedOrganizerService.unfollowOrganizer(UUID.fromString(jwt.getSubject()), organizerId);
    }
}
