package nl.loc.backend.event.service;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import nl.loc.backend.city.model.City;
import nl.loc.backend.event.dto.response.EventPage;
import nl.loc.backend.event.dto.response.EventRail;
import nl.loc.backend.event.dto.response.HomeView;
import nl.loc.backend.event.model.BrowseCriteria;
import nl.loc.backend.event.model.Place;
import nl.loc.backend.event.model.RailMode;
import nl.loc.backend.event.model.When;
import nl.loc.backend.event.repository.PublicEventRepository;
import org.springframework.stereotype.Service;

@Service
public class HomeService {
    public static final int RAIL_SIZE = 6;
    private final PublicEventRepository repository;
    private final EventBrowseService browse;
    private final Clock clock;

    public HomeService(PublicEventRepository repository, EventBrowseService browse, Clock clock) {
        this.repository = repository;
        this.browse = browse;
        this.clock = clock;
    }

    public HomeView home(String slug) {
        Instant now = clock.instant();
        City city = repository.findCity(slug).orElseGet(() -> City.fromSlug(slug));
        // Rails are optional highlights: a rail with no matches is left out, never padded or replaced.
        // The full event list is not part of home; the frontend reads it from GET /api/events.
        List<EventRail> rails = Stream.of(
                        rail(RailMode.TONIGHT, null, When.TONIGHT, Place.PHYSICAL, now),
                        rail(RailMode.WEEKEND, null, When.WEEKEND, Place.PHYSICAL, now),
                        rail(RailMode.NEAR_YOU, slug, When.UPCOMING, Place.PHYSICAL, now),
                        rail(RailMode.ONLINE, null, When.UPCOMING, Place.ONLINE, now))
                .filter(rail -> rail.total() > 0)
                .toList();
        return new HomeView(city, rails);
    }

    private EventRail rail(RailMode mode, String city, When when, Place place, Instant now) {
        EventPage page = browse.search(BrowseCriteria.rail(city, when, place, RAIL_SIZE), now);
        return new EventRail(mode, page.totalElements(), page.items(), page.totalElements() > page.items().size(),
                filters(city, when, place));
    }

    /** The /api/events query parameters that reproduce this rail; used for "See all". */
    private static Map<String, String> filters(String city, When when, Place place) {
        Map<String, String> filters = new LinkedHashMap<>();
        if (city != null) {
            filters.put("city", city);
        }
        filters.put("when", when.name().toLowerCase(Locale.ROOT));
        filters.put("place", place.name().toLowerCase(Locale.ROOT));
        return filters;
    }
}
