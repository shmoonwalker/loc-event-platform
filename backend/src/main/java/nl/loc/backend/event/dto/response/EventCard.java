package nl.loc.backend.event.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import nl.loc.backend.event.model.Place;
import java.util.UUID;

/** Homepage and results card. Organizer name is intentionally absent; it belongs on the event detail. */
@Schema(description = "Event card for the homepage and the events collection. Organizer name is not included.")
public record EventCard(
        @Schema(description = "Published event id.") UUID id,
        @Schema(description = "Event title.", example = "Late night jazz") String title,
        @Schema(description = "Occurrence start, UTC.") Instant startAt,
        @Schema(description = "Occurrence end, UTC. Equals the start when the snapshot has no end.") Instant endAt,
        @Schema(description = "Explicit location type: PHYSICAL or ONLINE.") Place place,
        @Schema(description = "City slug for a physical event.", example = "amsterdam") String citySlug,
        @Schema(description = "City display name.", example = "Amsterdam") String cityName,
        @Schema(description = "Venue name for a physical event.") String venueName,
        @Schema(description = "First image URL, when the snapshot has one.") String imageUrl
) {
}
