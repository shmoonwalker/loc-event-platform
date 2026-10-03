package nl.loc.backend.event.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import nl.loc.backend.category.model.EventCategory;
import nl.loc.backend.city.model.City;
import nl.loc.backend.event.dto.request.EventsQuery;
import nl.loc.backend.event.model.BrowseCriteria;
import nl.loc.backend.event.model.EventSort;
import nl.loc.backend.event.model.Place;
import nl.loc.backend.event.model.When;
import nl.loc.backend.tag.model.EventTag;
import org.springframework.stereotype.Component;

@Component
public class BrowseCriteriaParser {

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 20;
    public static final int MAX_PAGE = 50;
    public static final int MAX_QUERY_LENGTH = 100;

    public BrowseCriteria parse(EventsQuery query) {
        String q = searchText(query.getQ());
        String city = citySlug(query.getCity());
        List<EventCategory> categories = categories(query.getCategory());
        List<EventTag> tags = tags(query.getTag());
        When when = when(query.getWhen());
        Place place = place(query.getPlace());
        EventSort sort = sort(query.getSort(), q);
        int page = page(query.getPage());
        int size = size(query.getSize());
        return new BrowseCriteria(q, city, categories, tags, when, sort, place, page, size);
    }

    public String searchText(String raw) {
        String q = blankToNull(raw);
        if (q != null && q.length() > MAX_QUERY_LENGTH) {
            throw new InvalidBrowseQueryException("q must be at most " + MAX_QUERY_LENGTH + " characters");
        }
        return q;
    }

    public String homeCity(String city) {
        if (city == null || city.isBlank()) {
            return City.DEFAULT_SLUG;
        }
        String slug = citySlug(city);
        if (slug == null) {
            return City.DEFAULT_SLUG;
        }
        return slug;
    }

    private static String citySlug(String city) {
        if (city == null || city.isBlank()) {
            return null;
        }
        String slug = city.strip().toLowerCase(Locale.ROOT);
        if (!slug.matches("[a-z0-9]+(?:-[a-z0-9]+)*")) {
            throw new InvalidBrowseQueryException("city must be a slug such as amsterdam");
        }
        return slug;
    }

    private static List<EventCategory> categories(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<EventCategory> categories = new ArrayList<>();
        for (String value : raw) {
            if (value == null || value.isBlank()) {
                continue;
            }
            categories.add(EventCategory.fromSlug(value)
                    .orElseThrow(() -> new InvalidBrowseQueryException("Unknown category: " + value.strip())));
        }
        return List.copyOf(categories);
    }

    private static List<EventTag> tags(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<EventTag> tags = new ArrayList<>();
        for (String value : raw) {
            if (value == null || value.isBlank()) {
                continue;
            }
            tags.add(EventTag.fromSlug(value)
                    .orElseThrow(() -> new InvalidBrowseQueryException("Unknown tag: " + value.strip())));
        }
        return List.copyOf(tags);
    }

    private static When when(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return switch (raw.strip().toLowerCase(Locale.ROOT)) {
            case "tonight" -> When.TONIGHT;
            case "weekend" -> When.WEEKEND;
            case "upcoming" -> When.UPCOMING;
            default -> throw new InvalidBrowseQueryException("when must be tonight, weekend, or upcoming");
        };
    }

    private static Place place(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return switch (raw.strip().toLowerCase(Locale.ROOT)) {
            case "online" -> Place.ONLINE;
            case "physical" -> Place.PHYSICAL;
            default -> throw new InvalidBrowseQueryException("place must be online or physical");
        };
    }

    private static EventSort sort(String raw, String q) {
        if (raw == null || raw.isBlank()) {
            return q == null ? EventSort.START_TIME : EventSort.RELEVANCE;
        }
        return switch (raw.strip().toLowerCase(Locale.ROOT)) {
            case "start_time" -> EventSort.START_TIME;
            case "relevance" -> EventSort.RELEVANCE;
            default -> throw new InvalidBrowseQueryException("sort must be start_time or relevance");
        };
    }

    private static int page(Integer page) {
        int value = page == null ? 0 : page;
        if (value < 0 || value > MAX_PAGE) {
            throw new InvalidBrowseQueryException("page must be between 0 and " + MAX_PAGE);
        }
        return value;
    }

    private static int size(Integer size) {
        int value = size == null ? DEFAULT_PAGE_SIZE : size;
        if (value < 1 || value > MAX_PAGE_SIZE) {
            throw new InvalidBrowseQueryException("size must be between 1 and " + MAX_PAGE_SIZE);
        }
        return value;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip();
    }
}
