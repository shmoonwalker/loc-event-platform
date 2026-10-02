package nl.loc.backend.event.search;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import nl.loc.backend.category.dto.response.CategoryCount;
import nl.loc.backend.city.model.City;
import nl.loc.backend.event.dto.response.EventPage;
import nl.loc.backend.event.model.BrowseCriteria;
import nl.loc.backend.tag.dto.response.TagChip;

/**
 * Browse read model over {@code publication.discoverable_events}.
 * {@link PostgresPublicEventSearch} is the implementation.
 */
public interface PublicEventSearch {

    EventPage search(BrowseCriteria criteria, Instant now);

    List<CategoryCount> categories(String citySlug, Instant now);

    List<TagChip> tags(String citySlug, Instant now, int limit);

    Optional<City> findCity(String slug);

    List<City> suggestCities(String q, int limit);
}
