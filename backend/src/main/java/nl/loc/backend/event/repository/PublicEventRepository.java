package nl.loc.backend.event.repository;

import java.util.List;
import java.util.Optional;
import nl.loc.backend.city.model.City;
import nl.loc.backend.event.dto.response.EventPage;
import nl.loc.backend.event.model.BrowseCriteria;
import nl.loc.backend.event.model.TimeWindow;
import nl.loc.backend.event.model.EventSort;

/**
 * Browse read model over {@code publication.discoverable_events}.
 * {@link PostgresPublicEventRepository} is the implementation.
 */
public interface PublicEventRepository {

    EventPage search(BrowseCriteria criteria, TimeWindow.Range range, EventSort sort);

    Optional<City> findCity(String slug);

    List<City> suggestCities(String q, int limit, TimeWindow.Range range);
}
