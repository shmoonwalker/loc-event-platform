package nl.loc.backend.event.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import nl.loc.backend.city.model.City;
import nl.loc.backend.event.dto.response.EventPage;
import nl.loc.backend.event.dto.response.EventRail;
import nl.loc.backend.event.dto.response.HomeView;
import nl.loc.backend.event.model.BrowseCriteria;
import nl.loc.backend.event.model.BrowseLimits;
import nl.loc.backend.event.model.Place;
import nl.loc.backend.event.model.RailMode;
import nl.loc.backend.event.model.When;
import nl.loc.backend.event.repository.PublicEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

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
        EventRail tonight = rail(RailMode.TONIGHT, null, When.TONIGHT, Place.PHYSICAL, now);
        if (tonight.total() == 0) {
            // Only homepage discovery broadens an empty request. Explicit browse filters stay strict.
            tonight = rail(RailMode.STARTING_SOON, null, null, Place.PHYSICAL, now);
        }
        return new HomeView(city, tonight,
                rail(RailMode.WEEKEND, null, When.WEEKEND, Place.PHYSICAL, now),
                rail(RailMode.NEAR_YOU, slug, When.UPCOMING, Place.PHYSICAL, now),
                rail(RailMode.ONLINE, null, When.UPCOMING, Place.ONLINE, now),
                browseUrl(null, null, null));
    }

    private EventRail rail(RailMode mode, String city, When when, Place place, Instant now) {
        EventPage page = browse.search(BrowseCriteria.rail(city, when, place, RAIL_SIZE), now);
        return new EventRail(mode, page.totalElements(), page.items(), page.totalElements() > page.items().size(),
                browseUrl(city, when, place), browseUrl(null, null, place));
    }

    private static String browseUrl(String city, When when, Place place) {
        UriComponentsBuilder url = UriComponentsBuilder.fromPath("/api/events");
        if (city != null) {
            url.queryParam("city", city);
        }
        if (when != null) {
            url.queryParam("when", when.name().toLowerCase(Locale.ROOT));
        }
        if (place != null) {
            url.queryParam("place", place.name().toLowerCase(Locale.ROOT));
        }
        return url.queryParam("sort", "start_time").queryParam("page", 0)
                .queryParam("size", BrowseLimits.DEFAULT_PAGE_SIZE).build().encode().toUriString();
    }
}
