package nl.loc.backend.event.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import nl.loc.backend.event.model.Place;

@Schema(description = "Full public event page for one show and all its performances. "
        + "Online events have null address, city, venue, and coordinates.")
public record EventDetail(
        @Schema(description = "Show id. Shared by all performances of the same show.") UUID id,
        String title,
        String description,
        Place place,
        @Schema(description = "Original listing of the next date, when the source has a public one.") String sourceUrl,
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
        @Schema(description = "Upcoming dates, soonest first. Never empty. For an unavailable saved event: all its dates.")
        List<Occurrence> occurrences,
        @Schema(description = "Forecast for the next date. Null for online events or when no current forecast exists.") Weather weather,
        @Schema(description = "True when the signed-in member saved this event. Always false when signed out.") boolean saved,
        @Schema(description = "How many members saved this event.") long saveCount,
        @Schema(description = "False when the event was withdrawn or is over. Always true on the public event page.")
        boolean available
) {
    public record Occurrence(
            Instant startAt,
            Instant endAt,
            @Schema(description = "Ticket link for this date, when the source has a public one.") String sourceUrl) {
    }

    @Schema(description = "weatherCode is the WMO code from the forecast provider.")
    public record Weather(Instant forecastFetchedAt, Instant forecastHour, Double temperatureCelsius,
                          Integer precipitationProbabilityPercent, Double windSpeedKmh, Integer weatherCode) {
    }
}
