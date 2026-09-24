package nl.loc.data.event;

import java.time.Instant;
import java.util.List;

public record NormalizedEvent(
        String source,
        String externalId,
        String rawObjectKey,
        String title,
        String description,
        String sourceUrl,
        String registrationUrl,
        List<String> organizerNames,
        Instant sourceCreatedAt,
        Instant sourceUpdatedAt,
        EventLocation location,
        List<EventTimeSlot> timeSlots
) {
}
