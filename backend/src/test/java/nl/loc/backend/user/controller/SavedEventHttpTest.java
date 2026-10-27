package nl.loc.backend.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import nl.loc.backend.user.model.UserAccount;
import nl.loc.backend.user.repository.UserAccountRepository;
import nl.loc.backend.user.service.AccessTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
// nothing listens on port 1, so the rate limiter fails open and never limits these tests
@TestPropertySource(properties = "spring.data.redis.url=redis://localhost:1")
@Sql(scripts = "/db/publication-discoverable-events.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class SavedEventHttpTest {

    private static final UUID EVENT_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserAccountRepository userRepository;
    @Autowired
    private AccessTokenService accessTokenService;
    @Autowired
    private JdbcClient jdbc;

    private UserAccount alice;
    private UserAccount bob;

    @BeforeEach
    void setUp() {
        // deleting users cascades to their saved events
        userRepository.deleteAll();
        jdbc.sql("DELETE FROM publication.event_snapshot").update();
        alice = userRepository.save(new UserAccount("alice@example.com", "Alice", "hash", Instant.now()));
        bob = userRepository.save(new UserAccount("bob@example.com", "Bob", "hash", Instant.now()));
        publish(EVENT_ID);
    }

    @Test
    void saveAnswersNoContentAndStoresTheEvent() throws Exception {
        perform(put("/api/me/saved-events/{id}", EVENT_ID), token(alice)).andExpect(status().isNoContent());

        assertThat(savedEventIds(alice)).containsExactly(EVENT_ID);
    }

    @Test
    void savingTwiceKeepsOneSave() throws Exception {
        perform(put("/api/me/saved-events/{id}", EVENT_ID), token(alice)).andExpect(status().isNoContent());
        perform(put("/api/me/saved-events/{id}", EVENT_ID), token(alice)).andExpect(status().isNoContent());

        assertThat(savedEventIds(alice)).containsExactly(EVENT_ID);
    }

    @Test
    void savingAnUnknownEventAnswersNotFound() throws Exception {
        perform(put("/api/me/saved-events/{id}", UUID.randomUUID()), token(alice)).andExpect(status().isNotFound());

        assertThat(savedEventIds(alice)).isEmpty();
    }

    @Test
    void savingWithAnIdThatIsNotAUuidAnswersBadRequest() throws Exception {
        perform(put("/api/me/saved-events/not-a-uuid"), token(alice)).andExpect(status().isBadRequest());
    }

    @Test
    void savingAfterTheAccountWasDeletedAnswersUnauthorized() throws Exception {
        String token = token(alice);
        userRepository.delete(alice);

        perform(put("/api/me/saved-events/{id}", EVENT_ID), token).andExpect(status().isUnauthorized());
    }

    @Test
    void unsaveRemovesOnlyTheCallersSave() throws Exception {
        perform(put("/api/me/saved-events/{id}", EVENT_ID), token(alice)).andExpect(status().isNoContent());
        perform(put("/api/me/saved-events/{id}", EVENT_ID), token(bob)).andExpect(status().isNoContent());

        perform(delete("/api/me/saved-events/{id}", EVENT_ID), token(alice)).andExpect(status().isNoContent());

        assertThat(savedEventIds(alice)).isEmpty();
        assertThat(savedEventIds(bob)).containsExactly(EVENT_ID);
    }

    @Test
    void unsavingAnEventThatIsNotSavedAnswersNoContent() throws Exception {
        perform(delete("/api/me/saved-events/{id}", UUID.randomUUID()), token(alice)).andExpect(status().isNoContent());
    }

    @Test
    void signedOutCallsAnswerUnauthorized() throws Exception {
        mockMvc.perform(put("/api/me/saved-events/{id}", EVENT_ID)).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/me/saved-events/{id}", EVENT_ID)).andExpect(status().isUnauthorized());

        assertThat(savedEventIds(alice)).isEmpty();
    }

    @Test
    void listShowsTheMembersSavedEventsAsAPage() throws Exception {
        perform(put("/api/me/saved-events/{id}", EVENT_ID), token(alice)).andExpect(status().isNoContent());

        perform(get("/api/me/saved-events"), token(alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.items[0].event.id").value(EVENT_ID.toString()))
                .andExpect(jsonPath("$.items[0].event.title").value("Late night jazz"))
                .andExpect(jsonPath("$.items[0].savedAt").isNotEmpty())
                .andExpect(jsonPath("$.items[0].available").value(true));
    }

    @Test
    void listIsEmptyForAMemberWithoutSaves() throws Exception {
        perform(put("/api/me/saved-events/{id}", EVENT_ID), token(alice)).andExpect(status().isNoContent());

        perform(get("/api/me/saved-events"), token(bob))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0))
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void listRejectsInvalidPaging() throws Exception {
        perform(get("/api/me/saved-events").param("page", "-1"), token(alice)).andExpect(status().isBadRequest());
        perform(get("/api/me/saved-events").param("size", "0"), token(alice)).andExpect(status().isBadRequest());
        perform(get("/api/me/saved-events").param("size", "21"), token(alice)).andExpect(status().isBadRequest());
    }

    @Test
    void signedOutListAnswersUnauthorized() throws Exception {
        mockMvc.perform(get("/api/me/saved-events")).andExpect(status().isUnauthorized());
    }

    @Test
    void publicCardsShowWhetherTheSignedInMemberSavedTheEvent() throws Exception {
        perform(put("/api/me/saved-events/{id}", EVENT_ID), token(alice)).andExpect(status().isNoContent());

        perform(get("/api/events"), token(alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(EVENT_ID.toString()))
                .andExpect(jsonPath("$.items[0].saved").value(true));
        perform(get("/api/events"), token(bob)).andExpect(jsonPath("$.items[0].saved").value(false));
        mockMvc.perform(get("/api/events")).andExpect(jsonPath("$.items[0].saved").value(false));
    }

    @Test
    void homeRailCardsShowWhetherTheSignedInMemberSavedTheEvent() throws Exception {
        perform(put("/api/me/saved-events/{id}", EVENT_ID), token(alice)).andExpect(status().isNoContent());

        perform(get("/api/home"), token(alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rails[0].items[0].saved").value(true));
        mockMvc.perform(get("/api/home")).andExpect(jsonPath("$.rails[0].items[0].saved").value(false));
    }

    @Test
    void eventDetailShowsWhetherTheSignedInMemberSavedTheEvent() throws Exception {
        perform(put("/api/me/saved-events/{id}", EVENT_ID), token(alice)).andExpect(status().isNoContent());

        perform(get("/api/events/{id}", EVENT_ID), token(alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saved").value(true));
        perform(get("/api/events/{id}", EVENT_ID), token(bob)).andExpect(jsonPath("$.saved").value(false));
        mockMvc.perform(get("/api/events/{id}", EVENT_ID)).andExpect(jsonPath("$.saved").value(false));
    }

    @Test
    void eventDetailShowsHowManyMembersSavedTheEvent() throws Exception {
        mockMvc.perform(get("/api/events/{id}", EVENT_ID)).andExpect(jsonPath("$.saveCount").value(0));

        perform(put("/api/me/saved-events/{id}", EVENT_ID), token(alice)).andExpect(status().isNoContent());
        perform(put("/api/me/saved-events/{id}", EVENT_ID), token(bob)).andExpect(status().isNoContent());
        perform(put("/api/me/saved-events/{id}", EVENT_ID), token(bob)).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/events/{id}", EVENT_ID)).andExpect(jsonPath("$.saveCount").value(2));
        perform(get("/api/me/saved-events/{id}", EVENT_ID), token(alice)).andExpect(jsonPath("$.saveCount").value(2));

        perform(delete("/api/me/saved-events/{id}", EVENT_ID), token(bob)).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/events/{id}", EVENT_ID)).andExpect(jsonPath("$.saveCount").value(1));
    }

    @Test
    void memberCanOpenTheirSavedEvent() throws Exception {
        perform(put("/api/me/saved-events/{id}", EVENT_ID), token(alice)).andExpect(status().isNoContent());

        perform(get("/api/me/saved-events/{id}", EVENT_ID), token(alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(EVENT_ID.toString()))
                .andExpect(jsonPath("$.title").value("Late night jazz"))
                .andExpect(jsonPath("$.saved").value(true))
                .andExpect(jsonPath("$.available").value(true));
    }

    @Test
    void memberCanStillOpenASavedEventAfterItWasWithdrawn() throws Exception {
        perform(put("/api/me/saved-events/{id}", EVENT_ID), token(alice)).andExpect(status().isNoContent());
        jdbc.sql("UPDATE publication.event_snapshot SET discoverable = false WHERE loc_event_id = :id")
                .param("id", EVENT_ID).update();

        mockMvc.perform(get("/api/events/{id}", EVENT_ID)).andExpect(status().isNotFound());
        perform(get("/api/me/saved-events/{id}", EVENT_ID), token(alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.occurrences").isNotEmpty());
    }

    @Test
    void savedEventPageIsOnlyForTheMemberWhoSavedIt() throws Exception {
        UUID hidden = UUID.randomUUID();
        publish(hidden, false);
        perform(put("/api/me/saved-events/{id}", EVENT_ID), token(alice)).andExpect(status().isNoContent());

        perform(get("/api/me/saved-events/{id}", EVENT_ID), token(bob)).andExpect(status().isNotFound());
        perform(get("/api/me/saved-events/{id}", hidden), token(alice)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/me/saved-events/{id}", EVENT_ID)).andExpect(status().isUnauthorized());
    }

    @Test
    void publicEventPageIsAlwaysAvailable() throws Exception {
        mockMvc.perform(get("/api/events/{id}", EVENT_ID)).andExpect(jsonPath("$.available").value(true));
    }

    @Test
    void savedListCardsAreAlwaysSaved() throws Exception {
        perform(put("/api/me/saved-events/{id}", EVENT_ID), token(alice)).andExpect(status().isNoContent());

        perform(get("/api/me/saved-events"), token(alice)).andExpect(jsonPath("$.items[0].event.saved").value(true));
    }

    private String token(UserAccount user) {
        return accessTokenService.issueAccessToken(user);
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, String token) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private List<UUID> savedEventIds(UserAccount user) {
        return jdbc.sql("SELECT event_id FROM app.saved_event WHERE user_id = :userId")
                .param("userId", user.getId())
                .query(UUID.class)
                .list();
    }

    private void publish(UUID eventId) {
        publish(eventId, true);
    }

    private void publish(UUID eventId, boolean discoverable) {
        jdbc.sql("""
                INSERT INTO publication.event_snapshot (
                    loc_occurrence_id, loc_event_id, revision, discoverable, state, payload,
                    first_published_at, updated_at
                ) VALUES (
                    :occurrenceId, :eventId, 1, :discoverable, 'PUBLISHED',
                    CAST('{"title": "Late night jazz", "location": {"type": "ONLINE"},
                           "schedule": {"starts_at": "2998-01-01T20:00:00Z"},
                           "validUntil": "2999-01-01T00:00:00Z"}' AS jsonb), now(), now()
                )
                """)
                .param("occurrenceId", UUID.randomUUID())
                .param("eventId", eventId)
                .param("discoverable", discoverable)
                .update();
    }
}
