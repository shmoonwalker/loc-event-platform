package nl.loc.backend.user.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import nl.loc.backend.user.dto.response.SavedEventResponse;
import nl.loc.backend.user.model.UserAccount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@Testcontainers
@Sql(scripts = "/db/publication-discoverable-events.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class SavedEventRepositoryTest {

    private static final UUID EVENT_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

    private final SavedEventRepository repository;
    private final UserAccountRepository userRepository;
    private final JdbcClient jdbc;

    private UUID alice;
    private UUID bob;

    SavedEventRepositoryTest(SavedEventRepository repository, UserAccountRepository userRepository, JdbcClient jdbc) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.jdbc = jdbc;
    }

    @BeforeEach
    void createUsers() {
        // deleting users cascades to their saved events
        userRepository.deleteAll();
        jdbc.sql("DELETE FROM publication.event_snapshot").update();
        alice = userRepository.save(new UserAccount("alice@example.com", "Alice", "hash", Instant.now())).getId();
        bob = userRepository.save(new UserAccount("bob@example.com", "Bob", "hash", Instant.now())).getId();
    }

    @Test
    void savingTwiceKeepsOneRow() {
        repository.saveEvent(alice, EVENT_ID);
        repository.saveEvent(alice, EVENT_ID);

        assertThat(savedEventIds(alice)).containsExactly(EVENT_ID);
    }

    @Test
    void deleteRemovesOnlyThatMembersSave() {
        repository.saveEvent(alice, EVENT_ID);
        repository.saveEvent(bob, EVENT_ID);

        repository.deleteEvent(alice, EVENT_ID);

        assertThat(savedEventIds(alice)).isEmpty();
        assertThat(savedEventIds(bob)).containsExactly(EVENT_ID);
    }

    @Test
    void deletingAnEventThatIsNotSavedDoesNothing() {
        repository.deleteEvent(alice, EVENT_ID);

        assertThat(savedEventIds(alice)).isEmpty();
    }

    @Test
    void deletingTheAccountRemovesItsSaves() {
        repository.saveEvent(alice, EVENT_ID);

        userRepository.deleteById(alice);

        assertThat(savedEventIds(alice)).isEmpty();
    }

    @Test
    void eventExistsOnlyForDiscoverableEvents() {
        UUID hidden = UUID.randomUUID();
        UUID expired = UUID.randomUUID();
        publish(EVENT_ID, true, "2999-01-01T00:00:00Z");
        publish(hidden, false, "2999-01-01T00:00:00Z");
        publish(expired, true, "2000-01-01T00:00:00Z");

        assertThat(repository.eventExists(EVENT_ID)).isTrue();
        assertThat(repository.eventExists(hidden)).isFalse();
        assertThat(repository.eventExists(expired)).isFalse();
        assertThat(repository.eventExists(UUID.randomUUID())).isFalse();
    }

    @Test
    void countSavedCountsEachOfTheMembersEventsOnce() {
        UUID hidden = UUID.randomUUID();
        UUID withoutSnapshot = UUID.randomUUID();
        publish(EVENT_ID, true, "2999-01-01T00:00:00Z");
        publish(EVENT_ID, true, "2999-01-01T00:00:00Z");
        publish(hidden, false, "2999-01-01T00:00:00Z");
        repository.saveEvent(alice, EVENT_ID);
        repository.saveEvent(alice, hidden);
        repository.saveEvent(alice, withoutSnapshot);
        repository.saveEvent(bob, EVENT_ID);

        assertThat(repository.countSaved(alice)).isEqualTo(2);
        assertThat(repository.countSaved(bob)).isEqualTo(1);
    }

    @Test
    void findSavedListsTheMembersNewestSaveFirstAndPages() {
        UUID older = UUID.randomUUID();
        UUID newer = UUID.randomUUID();
        publish(older, true, "2999-01-01T00:00:00Z");
        publish(newer, true, "2999-01-01T00:00:00Z");
        repository.saveEvent(alice, older);
        repository.saveEvent(alice, newer);
        repository.saveEvent(bob, EVENT_ID);
        publish(EVENT_ID, true, "2999-01-01T00:00:00Z");
        savedAt(alice, older, "2030-01-01T00:00:00Z");
        savedAt(alice, newer, "2030-02-01T00:00:00Z");

        assertThat(repository.findSaved(alice, 0, 20)).extracting(saved -> saved.event().id())
                .containsExactly(newer, older);
        assertThat(repository.findSaved(alice, 1, 1)).extracting(saved -> saved.event().id())
                .containsExactly(older);
        assertThat(repository.findSaved(alice, 0, 20).getFirst().savedAt())
                .isEqualTo(Instant.parse("2030-02-01T00:00:00Z"));
    }

    @Test
    void findSavedKeepsHiddenAndExpiredEventsAsUnavailable() {
        UUID hidden = UUID.randomUUID();
        UUID expired = UUID.randomUUID();
        publish(EVENT_ID, true, "2999-01-01T00:00:00Z");
        publish(hidden, false, "2999-01-01T00:00:00Z");
        publish(expired, true, "2000-01-01T00:00:00Z");
        repository.saveEvent(alice, EVENT_ID);
        repository.saveEvent(alice, hidden);
        repository.saveEvent(alice, expired);

        assertThat(repository.findSaved(alice, 0, 20))
                .extracting(saved -> saved.event().id(), SavedEventResponse::available)
                .containsExactlyInAnyOrder(
                        tuple(EVENT_ID, true),
                        tuple(hidden, false),
                        tuple(expired, false));
    }

    @Test
    void findSavedShowsTheNextAvailableOccurrenceOtherwiseTheLatest() {
        UUID ended = UUID.randomUUID();
        publish(EVENT_ID, false, "2000-01-01T00:00:00Z", "1999-06-01T00:00:00Z");
        publish(EVENT_ID, true, "2999-01-01T00:00:00Z", "2998-03-01T00:00:00Z");
        publish(EVENT_ID, true, "2999-01-01T00:00:00Z", "2998-01-01T00:00:00Z");
        publish(ended, true, "2000-01-01T00:00:00Z", "1999-01-01T00:00:00Z");
        publish(ended, true, "2000-01-01T00:00:00Z", "1999-09-01T00:00:00Z");
        repository.saveEvent(alice, EVENT_ID);
        repository.saveEvent(alice, ended);

        assertThat(repository.findSaved(alice, 0, 20))
                .extracting(saved -> saved.event().id(), saved -> saved.event().startAt())
                .containsExactlyInAnyOrder(
                        tuple(EVENT_ID, Instant.parse("2998-01-01T00:00:00Z")),
                        tuple(ended, Instant.parse("1999-09-01T00:00:00Z")));
    }

    private void savedAt(UUID userId, UUID eventId, String savedAt) {
        jdbc.sql("UPDATE app.saved_event SET saved_at = CAST(:savedAt AS timestamptz) WHERE user_id = :userId AND event_id = :eventId")
                .param("savedAt", savedAt)
                .param("userId", userId)
                .param("eventId", eventId)
                .update();
    }

    private List<UUID> savedEventIds(UUID userId) {
        return jdbc.sql("SELECT event_id FROM app.saved_event WHERE user_id = :userId")
                .param("userId", userId)
                .query(UUID.class)
                .list();
    }

    private void publish(UUID eventId, boolean discoverable, String validUntil) {
        publish(eventId, discoverable, validUntil, validUntil);
    }

    // the view compares validUntil with the database clock, so dates are far from now
    private void publish(UUID eventId, boolean discoverable, String validUntil, String startsAt) {
        jdbc.sql("""
                INSERT INTO publication.event_snapshot (
                    loc_occurrence_id, loc_event_id, revision, discoverable, state, payload,
                    first_published_at, updated_at
                ) VALUES (
                    :occurrenceId, :eventId, 1, :discoverable, 'PUBLISHED', CAST(:payload AS jsonb), now(), now()
                )
                """)
                .param("occurrenceId", UUID.randomUUID())
                .param("eventId", eventId)
                .param("discoverable", discoverable)
                .param("payload", """
                        {"title": "Show", "location": {"type": "ONLINE"},
                         "schedule": {"starts_at": "%s"}, "validUntil": "%s"}
                        """.formatted(startsAt, validUntil))
                .update();
    }
}
