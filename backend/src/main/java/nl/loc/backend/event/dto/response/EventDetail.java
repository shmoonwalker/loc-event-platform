package nl.loc.backend.event.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import nl.loc.backend.event.model.Place;

@Schema(description = "Full public event page. Online events have null address, city, venue, and coordinates.")
public record EventDetail(
        UUID id,
        String title,
        String description,
        Place place,
        @Schema(description = "Original listing, when the source has a public one.") String sourceUrl,
        String venueName,
        String address,
        String postalCode,
        String citySlug,
        String cityName,
        Double latitude,
        Double longitude,
        @Schema(description = "Category value (slug) and label. Send value to /api/events?category=.") List<FilterOption> categories,
        @Schema(description = "Tag value (slug) and label. Send value to /api/events?tag=.") List<FilterOption> tags,
        List<String> imageUrls,
        List<OrganizerInfo> organizers,
        @Schema(description = "Upcoming dates, soonest first. Never empty.") List<Occurrence> occurrences
) {
    public record Occurrence(Instant startAt, Instant endAt) {
    }
}
