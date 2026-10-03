package nl.loc.backend.event.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import nl.loc.backend.city.model.City;
import nl.loc.backend.event.dto.response.EventPage;
import nl.loc.backend.event.model.BrowseCriteria;
import nl.loc.backend.event.model.EventSort;
import nl.loc.backend.event.model.TimeWindow;
import nl.loc.backend.event.repository.PublicEventRepository;
import org.springframework.stereotype.Service;

@Service
public class EventBrowseService {
    private final PublicEventRepository repository;
    private final Clock clock;

    public EventBrowseService(PublicEventRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public EventPage search(BrowseCriteria criteria) {
        return search(criteria, clock.instant());
    }

    EventPage search(BrowseCriteria criteria, Instant now) {
        TimeWindow.Range range = criteria.when() == null
                ? TimeWindow.future(now) : TimeWindow.forPreset(criteria.when(), now);
        if (criteria.dateFrom() != null) {
            range = TimeWindow.custom(criteria.dateFrom(), criteria.dateTo(), now);
        }
        EventSort sort = criteria.sort() == EventSort.RELEVANCE && criteria.q() == null
                ? EventSort.START_TIME : criteria.sort();
        return repository.search(criteria, range, sort);
    }

    public List<City> cities(String q) {
        return repository.suggestCities(q, 10, TimeWindow.future(clock.instant()));
    }
}
