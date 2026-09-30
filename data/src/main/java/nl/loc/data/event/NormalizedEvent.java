package nl.loc.data.event;

import java.time.Instant;
import java.util.List;

import nl.loc.data.tagging.ContentTag;

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
        List<String> qualificationIssues,
        List<ContentTag> sourceTags
) {
}
