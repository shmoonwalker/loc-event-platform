package nl.loc.backend.event.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Organizer with one page of their upcoming events, soonest first.")
public record OrganizerDetail(OrganizerInfo organizer, EventPage events) {
}
