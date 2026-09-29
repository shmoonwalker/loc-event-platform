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
        List<String> organizerNames,
        Instant sourceCreatedAt,
        Instant sourceUpdatedAt,
        EventLocation location,
        List<EventTimeSlot> timeSlots,
        EventLifecycle lifecycleStatus,
        List<EventImage> images,
        List<Category> categories,
        List<String> qualificationIssues
) {
    public NormalizedEvent(String source, String externalId, String rawObjectKey, String title,
                           String description, String sourceUrl, List<String> organizerNames,
                           Instant sourceCreatedAt, Instant sourceUpdatedAt, EventLocation location,
                           List<EventTimeSlot> timeSlots, EventLifecycle lifecycleStatus,
                           List<EventImage> images, List<Category> categories) {
        this(source, externalId, rawObjectKey, title, description, sourceUrl, organizerNames,
                sourceCreatedAt, sourceUpdatedAt, location, timeSlots, lifecycleStatus,
                images, categories, List.of());
    }
}
