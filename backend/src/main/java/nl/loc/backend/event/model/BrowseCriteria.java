package nl.loc.backend.event.model;

import java.util.List;
import java.time.LocalDate;
import java.time.LocalTime;
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
        LocalDate dateFrom,
        LocalDate dateTo,
        LocalTime timeFrom,
        LocalTime timeTo,
        int page,
        int size
) {

    public static BrowseCriteria rail(String citySlug, When when, Place place, int size) {
        return new BrowseCriteria(null, citySlug, List.of(), List.of(), when, EventSort.START_TIME, place, null, null, null, null, 0, size);
    }
}
