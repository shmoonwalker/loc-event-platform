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
import java.util.stream.Collectors;
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
class FollowedOrganizerHttpTest {

    private static final UUID JAZZ_CLUB = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
    private static final UUID THEATRE = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");
    private static final String FUTURE = "2998-01-01T20:00:00Z";

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
        // deleting users cascades to their follows
        userRepository.deleteAll();
        jdbc.sql("DELETE FROM publication.event_snapshot").update();
        jdbc.sql("DELETE FROM publication.organizer_identity").update();
        alice = userRepository.save(new UserAccount("alice@example.com", "Alice", "hash", Instant.now()));
        bob = userRepository.save(new UserAccount("bob@example.com", "Bob", "hash", Instant.now()));
        knownOrganizer(JAZZ_CLUB, "jazz-club");
        knownOrganizer(THEATRE, "theatre");
    }

    @Test
    void followAnswersNoContentAndStoresTheFollow() throws Exception {
        perform(put("/api/me/followed-organizers/{id}", JAZZ_CLUB), token(alice)).andExpect(status().isNoContent());

        assertThat(followedIds(alice)).containsExactly(JAZZ_CLUB);
    }

    @Test
    void followingTwiceKeepsOneRow() throws Exception {
        perform(put("/api/me/followed-organizers/{id}", JAZZ_CLUB), token(alice)).andExpect(status().isNoContent());
        perform(put("/api/me/followed-organizers/{id}", JAZZ_CLUB), token(alice)).andExpect(status().isNoContent());

        assertThat(followedIds(alice)).containsExactly(JAZZ_CLUB);
    }

    @Test
    void followingAnUnknownOrganizerAnswersNotFound() throws Exception {
        perform(put("/api/me/followed-organizers/{id}", UUID.randomUUID()), token(alice))
                .andExpect(status().isNotFound());

        assertThat(followedIds(alice)).isEmpty();
    }

    @Test
    void followingWithAnIdThatIsNotAUuidAnswersBadRequest() throws Exception {
        perform(put("/api/me/followed-organizers/not-a-uuid"), token(alice)).andExpect(status().isBadRequest());
    }

    @Test
    void followingAfterTheAccountWasDeletedAnswersUnauthorized() throws Exception {
        String token = token(alice);
        userRepository.delete(alice);

        perform(put("/api/me/followed-organizers/{id}", JAZZ_CLUB), token).andExpect(status().isUnauthorized());
    }

    @Test
    void unfollowRemovesOnlyTheCallersFollow() throws Exception {
        perform(put("/api/me/followed-organizers/{id}", JAZZ_CLUB), token(alice)).andExpect(status().isNoContent());
        perform(put("/api/me/followed-organizers/{id}", JAZZ_CLUB), token(bob)).andExpect(status().isNoContent());

        perform(delete("/api/me/followed-organizers/{id}", JAZZ_CLUB), token(alice)).andExpect(status().isNoContent());

        assertThat(followedIds(alice)).isEmpty();
        assertThat(followedIds(bob)).containsExactly(JAZZ_CLUB);
    }

    @Test
    void unfollowingAnOrganizerThatIsNotFollowedAnswersNoContent() throws Exception {
        perform(delete("/api/me/followed-organizers/{id}", UUID.randomUUID()), token(alice))
                .andExpect(status().isNoContent());
    }

    @Test
    void signedOutCallsAnswerUnauthorized() throws Exception {
        mockMvc.perform(put("/api/me/followed-organizers/{id}", JAZZ_CLUB)).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/me/followed-organizers/{id}", JAZZ_CLUB)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/me/followed-organizers")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/me/followed-organizers/events")).andExpect(status().isUnauthorized());

        assertThat(followedIds(alice)).isEmpty();
    }

    @Test
    void listShowsTheMembersFollowsMostRecentFirstWithDetailsFromTheSnapshots() throws Exception {
        publish(UUID.randomUUID(), FUTURE, true, JAZZ_CLUB);
        publish(UUID.randomUUID(), FUTURE, true, THEATRE);
        perform(put("/api/me/followed-organizers/{id}", JAZZ_CLUB), token(alice)).andExpect(status().isNoContent());
        perform(put("/api/me/followed-organizers/{id}", THEATRE), token(alice)).andExpect(status().isNoContent());

        perform(get("/api/me/followed-organizers"), token(alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(THEATRE.toString()))
                .andExpect(jsonPath("$[0].name").value("Organizer " + THEATRE))
                .andExpect(jsonPath("$[1].id").value(JAZZ_CLUB.toString()));
        perform(get("/api/me/followed-organizers"), token(bob))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void listKeepsAnOrganizerWhoseEventsAreAllOver() throws Exception {
        publish(UUID.randomUUID(), "2000-01-01T20:00:00Z", false, JAZZ_CLUB);
        perform(put("/api/me/followed-organizers/{id}", JAZZ_CLUB), token(alice)).andExpect(status().isNoContent());

        perform(get("/api/me/followed-organizers"), token(alice))
                .andExpect(jsonPath("$[0].id").value(JAZZ_CLUB.toString()));
    }

    @Test
    void eventsShowOnlyUpcomingVisibleEventsOfFollowedOrganizersSoonestFirst() throws Exception {
        UUID later = UUID.randomUUID();
        UUID sooner = UUID.randomUUID();
        UUID shared = UUID.randomUUID();
        publish(later, "2998-03-01T20:00:00Z", true, JAZZ_CLUB);
        publish(sooner, "2998-01-01T20:00:00Z", true, JAZZ_CLUB);
        // run by both followed organizers: still one card
        publish(shared, "2998-02-01T20:00:00Z", true, JAZZ_CLUB, THEATRE);
        publish(UUID.randomUUID(), "2000-01-01T20:00:00Z", true, JAZZ_CLUB);
        publish(UUID.randomUUID(), FUTURE, false, JAZZ_CLUB);
        UUID notFollowed = UUID.randomUUID();
        knownOrganizer(notFollowed, "other");
        publish(UUID.randomUUID(), FUTURE, true, notFollowed);
        perform(put("/api/me/followed-organizers/{id}", JAZZ_CLUB), token(alice)).andExpect(status().isNoContent());
        perform(put("/api/me/followed-organizers/{id}", THEATRE), token(alice)).andExpect(status().isNoContent());

        perform(get("/api/me/followed-organizers/events"), token(alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[0].id").value(sooner.toString()))
                .andExpect(jsonPath("$.items[1].id").value(shared.toString()))
                .andExpect(jsonPath("$.items[2].id").value(later.toString()));
    }

    @Test
    void eventsArePagedAndShowWhetherTheMemberSavedThem() throws Exception {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        publish(first, "2998-01-01T20:00:00Z", true, JAZZ_CLUB);
        publish(second, "2998-02-01T20:00:00Z", true, JAZZ_CLUB);
        perform(put("/api/me/followed-organizers/{id}", JAZZ_CLUB), token(alice)).andExpect(status().isNoContent());
        perform(put("/api/me/saved-events/{id}", second), token(alice)).andExpect(status().isNoContent());

        perform(get("/api/me/followed-organizers/events").param("page", "1").param("size", "1"), token(alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.items[0].id").value(second.toString()))
                .andExpect(jsonPath("$.items[0].saved").value(true));
    }

    @Test
    void eventsAreEmptyForAMemberWithoutFollows() throws Exception {
        publish(UUID.randomUUID(), FUTURE, true, JAZZ_CLUB);
        perform(put("/api/me/followed-organizers/{id}", JAZZ_CLUB), token(alice)).andExpect(status().isNoContent());

        perform(get("/api/me/followed-organizers/events"), token(bob))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0))
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void eventsRejectInvalidPaging() throws Exception {
        perform(get("/api/me/followed-organizers/events").param("page", "-1"), token(alice))
                .andExpect(status().isBadRequest());
        perform(get("/api/me/followed-organizers/events").param("size", "0"), token(alice))
                .andExpect(status().isBadRequest());
        perform(get("/api/me/followed-organizers/events").param("size", "21"), token(alice))
                .andExpect(status().isBadRequest());
    }

    @Test
    void organizerDetailShowsWhetherTheSignedInMemberFollowsIt() throws Exception {
        publish(UUID.randomUUID(), FUTURE, true, JAZZ_CLUB);
        perform(put("/api/me/followed-organizers/{id}", JAZZ_CLUB), token(alice)).andExpect(status().isNoContent());

        perform(get("/api/organizers/{id}", JAZZ_CLUB), token(alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.followed").value(true));
        perform(get("/api/organizers/{id}", JAZZ_CLUB), token(bob)).andExpect(jsonPath("$.followed").value(false));
        mockMvc.perform(get("/api/organizers/{id}", JAZZ_CLUB)).andExpect(jsonPath("$.followed").value(false));
    }

    private String token(UserAccount user) {
        return accessTokenService.issueAccessToken(user);
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, String token) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private List<UUID> followedIds(UserAccount user) {
        return jdbc.sql("SELECT organizer_id FROM app.followed_organizer WHERE user_id = :userId")
                .param("userId", user.getId())
                .query(UUID.class)
                .list();
    }

    private void knownOrganizer(UUID organizerId, String externalId) {
        jdbc.sql("""
                INSERT INTO publication.organizer_identity (loc_organizer_id, source, external_id)
                VALUES (:id, 'test', :externalId)
                """)
                .param("id", organizerId)
                .param("externalId", externalId)
                .update();
    }

    private void publish(UUID eventId, String startsAt, boolean discoverable, UUID... organizerIds) {
        String organizers = List.of(organizerIds).stream()
                .map(id -> "{\"locOrganizerId\": \"" + id + "\", \"name\": \"Organizer " + id + "\"}")
                .collect(Collectors.joining(", ", "[", "]"));
        jdbc.sql("""
                INSERT INTO publication.event_snapshot (
                    loc_occurrence_id, loc_event_id, revision, discoverable, state, payload,
                    first_published_at, updated_at
                ) VALUES (
                    :occurrenceId, :eventId, 1, :discoverable, 'PUBLISHED',
                    jsonb_build_object(
                        'title', 'Late night jazz',
                        'location', jsonb_build_object('type', 'ONLINE'),
                        'schedule', jsonb_build_object('starts_at', :startsAt),
                        'validUntil', '2999-01-01T00:00:00Z',
                        'organizers', CAST(:organizers AS jsonb)),
                    now(), now()
                )
                """)
                .param("occurrenceId", UUID.randomUUID())
                .param("eventId", eventId)
                .param("discoverable", discoverable)
                .param("startsAt", startsAt)
                .param("organizers", organizers)
                .update();
    }
}
