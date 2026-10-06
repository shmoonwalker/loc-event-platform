package nl.loc.backend.event.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Organizer with their upcoming events, soonest first, at most 20.")
public record OrganizerDetail(OrganizerInfo organizer, List<EventCard> events) {
}
