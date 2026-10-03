package nl.loc.backend.event.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class TimeWindowTest {
    @Test
    void tonightFollowsAmsterdamMidnight() {
        Instant now = Instant.parse("2030-06-07T22:00:00Z");
        TimeWindow.Range range = TimeWindow.tonight(now);
        assertThat(range.startsFrom()).isEqualTo(now);
        assertThat(range.startsBefore()).isEqualTo(Instant.parse("2030-06-08T22:00:00Z"));
        assertThat(range.endsAfter()).isEqualTo(now);
    }

    @Test
    void sundayUsesTheCurrentWeekendAndMondayUsesTheNext() {
        TimeWindow.Range sunday = TimeWindow.weekend(Instant.parse("2030-06-09T12:00:00Z"));
        assertThat(sunday.startsFrom()).isEqualTo(Instant.parse("2030-06-07T22:00:00Z"));
        assertThat(sunday.startsBefore()).isEqualTo(Instant.parse("2030-06-09T22:00:00Z"));
        TimeWindow.Range monday = TimeWindow.weekend(Instant.parse("2030-06-10T12:00:00Z"));
        assertThat(monday.startsFrom()).isEqualTo(Instant.parse("2030-06-14T22:00:00Z"));
    }

    @Test
    void calendarDaysRespectBothDaylightSavingTransitions() {
        TimeWindow.Range spring = TimeWindow.tonight(Instant.parse("2026-03-29T12:00:00Z"));
        TimeWindow.Range autumn = TimeWindow.tonight(Instant.parse("2026-10-25T12:00:00Z"));
        assertThat(Duration.between(spring.startsFrom(), spring.startsBefore())).isEqualTo(Duration.ofHours(23));
        assertThat(Duration.between(autumn.startsFrom(), autumn.startsBefore())).isEqualTo(Duration.ofHours(25));
    }
}
