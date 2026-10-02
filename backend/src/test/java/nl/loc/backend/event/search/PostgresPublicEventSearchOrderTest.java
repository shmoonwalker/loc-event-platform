package nl.loc.backend.event.search;

import static org.assertj.core.api.Assertions.assertThat;

import nl.loc.backend.event.model.EventSort;
import org.junit.jupiter.api.Test;

class PostgresPublicEventSearchOrderTest {

    @Test
    void equalStartTimesBreakTiesByEventId() {
        assertThat(PostgresPublicEventSearch.orderBy(EventSort.START_TIME)).isEqualTo("start_at, loc_event_id");
    }

    @Test
    void relevanceKeepsEventIdAsTheLastTieBreak() {
        String order = PostgresPublicEventSearch.orderBy(EventSort.RELEVANCE);
        assertThat(order).endsWith("start_at, loc_event_id");
        assertThat(order).startsWith("ts_rank(document, websearch_to_tsquery('simple', :q)) DESC, ");
    }
}
