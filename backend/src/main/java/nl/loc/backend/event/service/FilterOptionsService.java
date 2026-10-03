package nl.loc.backend.event.service;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import nl.loc.backend.category.model.EventCategory;
import nl.loc.backend.event.dto.response.FilterOption;
import nl.loc.backend.event.dto.response.FilterOptions;
import nl.loc.backend.event.model.BrowseLimits;
import nl.loc.backend.event.model.TimeWindow;
import nl.loc.backend.tag.model.EventTag;
import org.springframework.stereotype.Service;

@Service
public class FilterOptionsService {
    public FilterOptions options() {
        return new FilterOptions(
                Arrays.stream(EventCategory.values()).map(c -> new FilterOption(c.slug(), c.label())).toList(),
                Arrays.stream(EventTag.values()).map(t -> new FilterOption(t.slug(), t.label()))
                        .sorted(Comparator.comparing(FilterOption::label, String.CASE_INSENSITIVE_ORDER)).toList(),
                List.of(new FilterOption("start_time", "Soonest first"), new FilterOption("relevance", "Relevance")),
                List.of(new FilterOption("tonight", "Tonight"), new FilterOption("weekend", "This weekend"),
                        new FilterOption("upcoming", "Next 30 days")),
                List.of(new FilterOption("physical", "In person"), new FilterOption("online", "Online")),
                TimeWindow.ZONE.getId(), BrowseLimits.DEFAULT_PAGE_SIZE, BrowseLimits.MAX_PAGE_SIZE, BrowseLimits.MAX_QUERY_LENGTH);
    }
}
