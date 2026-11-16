package nl.loc.backend.admin.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import nl.loc.backend.admin.dto.HiddenEventResponse;
import nl.loc.backend.admin.dto.HideEventRequest;
import nl.loc.backend.admin.service.HiddenEventService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin moderation", description = "Hide events from public discovery. Admin only.")
public class HiddenEventController {

    private final HiddenEventService hiddenEventService;

    public HiddenEventController(HiddenEventService hiddenEventService) {
        this.hiddenEventService = hiddenEventService;
    }

    @PostMapping("/events/{eventId}/hide")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Hide an event",
            description = "Hides the show from public discovery. eventId is a show id or an older event id. "
                    + "The publication row is not changed. Hiding a show that is already hidden keeps the first row "
                    + "and still closes any new open reports of that event. Those reports are resolved as HIDDEN.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "The event is hidden."),
            @ApiResponse(
                    responseCode = "400",
                    description = "The event id is not a UUID, or the reason is missing, blank, or longer than 1000 "
                            + "characters.",
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
                    description = "The event is unknown.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public void hide(@AuthenticationPrincipal Jwt jwt,
                     @Parameter(description = "Show id, or an older event id.") @PathVariable UUID eventId,
                     @Valid @RequestBody HideEventRequest request) {
        hiddenEventService.hide(UUID.fromString(jwt.getSubject()), eventId, request.reason());
    }

    @DeleteMapping("/events/{eventId}/hide")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Unhide an event",
            description = "Returns the show to public discovery. Unhiding a show that is not hidden changes nothing. "
                    + "Reports closed when it was hidden stay closed.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "The event is not hidden."),
            @ApiResponse(
                    responseCode = "400",
                    description = "The event id is not a UUID.",
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
                    description = "The event is unknown.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public void unhide(@AuthenticationPrincipal Jwt jwt,
                       @Parameter(description = "Show id, or an older event id.") @PathVariable UUID eventId) {
        hiddenEventService.unhide(UUID.fromString(jwt.getSubject()), eventId);
    }

    @GetMapping("/hidden-events")
    @Operation(
            summary = "Hidden events",
            description = "Events an admin has hidden, most recently hidden first, with who hid them, when, and why.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The hidden events."),
            @ApiResponse(
                    responseCode = "401",
                    description = "Missing, invalid or expired access token.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(
                    responseCode = "403",
                    description = "The access token is not an admin.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public List<HiddenEventResponse> hiddenEvents() {
        return hiddenEventService.hiddenEvents();
    }
}
