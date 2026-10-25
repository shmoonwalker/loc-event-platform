package nl.loc.backend.user.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import nl.loc.backend.event.controller.BrowseCriteriaParser;
import nl.loc.backend.event.dto.response.EventDetail;
import nl.loc.backend.user.dto.response.SavedEventPage;
import nl.loc.backend.user.service.SavedEventService;
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
@RequestMapping("/api/me/saved-events")
@Tag(name = "Saved events", description = "Events the signed-in member saved.")
public class SavedEventController {

    private final SavedEventService savedEventService;

    public SavedEventController(SavedEventService savedEventService) {
        this.savedEventService = savedEventService;
    }

    @GetMapping
    @Operation(
            summary = "Saved events",
            description = "One page of the saved events of the member of the access token in the Authorization header, "
                    + "most recently saved first (page from 0, size 1-20, default 20, same as /api/events). "
                    + "Withdrawn and past events stay in the list with available false.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "One page of saved events."),
            @ApiResponse(
                    responseCode = "400",
                    description = "page is negative, or size is not between 1 and 20.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(
                    responseCode = "401",
                    description = "Missing, invalid or expired access token.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public SavedEventPage savedEvents(@AuthenticationPrincipal Jwt jwt,
                                      @Parameter(description = "Zero-based page index. Defaults to 0.")
                                      @RequestParam(required = false) Integer page,
                                      @Parameter(description = "Page size from 1 to 20. Defaults to 20.")
                                      @RequestParam(required = false) Integer size) {
        return savedEventService.savedEvents(UUID.fromString(jwt.getSubject()),
                BrowseCriteriaParser.page(page), BrowseCriteriaParser.size(size));
    }

    @GetMapping("/{eventId}")
    @Operation(
            summary = "Saved event",
            description = "Full event page of an event the member of the access token in the Authorization header saved, "
                    + "also when it was withdrawn or is over (then available is false). Same shape as GET /api/events/{id}.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The saved event."),
            @ApiResponse(
                    responseCode = "400",
                    description = "The event id is not a UUID.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(
                    responseCode = "401",
                    description = "Missing, invalid or expired access token.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(
                    responseCode = "404",
                    description = "The member did not save this event, or it does not exist.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public EventDetail savedEvent(@AuthenticationPrincipal Jwt jwt,
                                  @Parameter(description = "Id of the saved event.") @PathVariable UUID eventId) {
        return savedEventService.savedEvent(UUID.fromString(jwt.getSubject()), eventId);
    }

    @PutMapping("/{eventId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Save an event",
            description = "Saves the event for the member of the access token in the Authorization header. "
                    + "Saving an event that is already saved changes nothing.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "The event is saved."),
            @ApiResponse(
                    responseCode = "400",
                    description = "The event id is not a UUID.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(
                    responseCode = "401",
                    description = "Missing, invalid or expired access token, or the account no longer exists.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(
                    responseCode = "404",
                    description = "The event is unknown, withdrawn, or over.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public void save(@AuthenticationPrincipal Jwt jwt,
                     @Parameter(description = "Id of the event to save.") @PathVariable UUID eventId) {
        savedEventService.saveEvent(UUID.fromString(jwt.getSubject()), eventId);
    }

    @DeleteMapping("/{eventId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Unsave an event",
            description = "Removes the event from the saved events of the member of the access token in the "
                    + "Authorization header. Unsaving an event that is not saved, or that is withdrawn or over, "
                    + "also answers 204.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "The event is not saved."),
            @ApiResponse(
                    responseCode = "400",
                    description = "The event id is not a UUID.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(
                    responseCode = "401",
                    description = "Missing, invalid or expired access token.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public void unsave(@AuthenticationPrincipal Jwt jwt,
                       @Parameter(description = "Id of the event to unsave.") @PathVariable UUID eventId) {
        savedEventService.unsaveEvent(UUID.fromString(jwt.getSubject()), eventId);
    }
}
