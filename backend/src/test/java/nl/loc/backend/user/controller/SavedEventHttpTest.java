package nl.loc.backend.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
        jdbc.sql("""
                INSERT INTO publication.event_snapshot (
                    loc_occurrence_id, loc_event_id, revision, discoverable, state, payload,
                    first_published_at, updated_at
                ) VALUES (
                    :occurrenceId, :eventId, 1, true, 'PUBLISHED',
                    CAST('{"validUntil": "2999-01-01T00:00:00Z"}' AS jsonb), now(), now()
                )
                """)
                .param("occurrenceId", UUID.randomUUID())
                .param("eventId", eventId)
                .update();
    }
}
