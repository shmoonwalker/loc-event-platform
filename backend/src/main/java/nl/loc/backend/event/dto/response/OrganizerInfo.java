package nl.loc.backend.event.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Public organizer details. description and site are null when the source gave none.")
public record OrganizerInfo(UUID id, String name, String description, String site) {
}
