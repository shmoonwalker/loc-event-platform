package nl.loc.backend.event.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import nl.loc.backend.category.dto.response.CategoryCount;
import nl.loc.backend.city.model.City;
import nl.loc.backend.city.model.CityNames;
import nl.loc.backend.event.dto.response.EventCard;
import nl.loc.backend.event.dto.response.EventPage;
import nl.loc.backend.event.dto.response.EventRail;
import nl.loc.backend.event.dto.response.HomeView;
import nl.loc.backend.event.model.BrowseCriteria;
import nl.loc.backend.event.model.Place;
import nl.loc.backend.event.model.When;
import nl.loc.backend.event.model.TimeWindow;
import nl.loc.backend.event.repository.PublicEventRepository;
import nl.loc.backend.tag.dto.response.TagChip;
import org.springframework.stereotype.Service;

@Service
public class HomeService {

    public static final int RAIL_SIZE = 6;
    public static final int TAG_LIMIT = 8;

    private final PublicEventRepository repository;
    private final EventBrowseService browse;
    private final Clock clock;

    public HomeService(PublicEventRepository repository, EventBrowseService browse, Clock clock) {
        this.repository = repository;
        this.browse = browse;
        this.clock = clock;
    }

    public HomeView home(String slug) {
        return load(slug, clock.instant());
    }

    private HomeView load(String slug, Instant now) {
        City nearYouCity = repository.findCity(slug).orElseGet(() -> new City(slug, CityNames.fromSlug(slug)));
        EventRail tonight = rail(null, When.TONIGHT, Place.PHYSICAL, now);
        EventRail weekend = rail(null, When.WEEKEND, Place.PHYSICAL, now);
        EventRail nearYou = rail(slug, When.UPCOMING, Place.PHYSICAL, now);
        EventRail online = rail(null, When.UPCOMING, Place.ONLINE, now);
        List<TagChip> tags = repository.tags(null, TimeWindow.facets(now), Place.PHYSICAL, TAG_LIMIT);
        List<CategoryCount> categories = repository.categories(null, TimeWindow.facets(now), Place.PHYSICAL);
        return new HomeView(nearYouCity, tags, categories, tonight, weekend, nearYou, online);
    }

    private EventRail rail(String citySlug, When when, Place place, Instant now) {
        EventPage page = browse.search(BrowseCriteria.rail(citySlug, when, place, RAIL_SIZE), now);
        List<EventCard> items = page.items();
        return new EventRail(page.totalElements(), items, page.totalElements() > items.size());
    }
}
