package nl.loc.backend.event.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
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
import nl.loc.backend.event.search.PublicEventSearch;
import nl.loc.backend.tag.dto.response.TagChip;
import org.springframework.stereotype.Service;

@Service
public class HomeService {

    public static final int RAIL_SIZE = 6;
    public static final int TAG_LIMIT = 8;
    private static final Duration CACHE_TTL = Duration.ofMinutes(1);

    private final PublicEventSearch search;
    private final BrowseCriteriaParser parser;
    private final ConcurrentHashMap<String, CachedHome> cache = new ConcurrentHashMap<>();

    public HomeService(PublicEventSearch search, BrowseCriteriaParser parser) {
        this.search = search;
        this.parser = parser;
    }

    public HomeView home(String city) {
        String slug = parser.homeCity(city);
        Instant now = Instant.now();
        CachedHome cached = cache.get(slug);
        if (cached != null && cached.expiresAt().isAfter(now)) {
            return cached.view();
        }
        HomeView view = load(slug, now);
        cache.put(slug, new CachedHome(view, now.plus(CACHE_TTL)));
        return view;
    }

    private HomeView load(String slug, Instant now) {
        City nearYouCity = search.findCity(slug).orElseGet(() -> new City(slug, CityNames.fromSlug(slug)));
        EventRail tonight = rail(null, When.TONIGHT, Place.PHYSICAL, now);
        EventRail weekend = rail(null, When.WEEKEND, Place.PHYSICAL, now);
        EventRail nearYou = rail(slug, When.UPCOMING, Place.PHYSICAL, now);
        EventRail online = rail(null, When.UPCOMING, Place.ONLINE, now);
        List<TagChip> tags = search.tags(null, now, TAG_LIMIT);
        List<CategoryCount> categories = search.categories(null, now);
        return new HomeView(nearYouCity, tags, categories, tonight, weekend, nearYou, online);
    }

    private EventRail rail(String citySlug, When when, Place place, Instant now) {
        EventPage page = search.search(BrowseCriteria.rail(citySlug, when, place, RAIL_SIZE), now);
        List<EventCard> items = page.items();
        return new EventRail(page.totalElements(), items, page.totalElements() > items.size());
    }

    private record CachedHome(HomeView view, Instant expiresAt) {
    }
}
