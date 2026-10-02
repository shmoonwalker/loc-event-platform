package nl.loc.backend.event.model;

import java.util.List;
import nl.loc.backend.category.model.EventCategory;
import nl.loc.backend.tag.model.EventTag;

public record BrowseCriteria(
        String q,
        String city,
        List<EventCategory> categories,
        List<EventTag> tags,
        When when,
        EventSort sort,
        Place place,
        int page,
        int size
) {

    public static BrowseCriteria rail(String citySlug, When when, Place place, int size) {
        return new BrowseCriteria(null, citySlug, List.of(), List.of(), when, EventSort.START_TIME, place, 0, size);
    }
}
