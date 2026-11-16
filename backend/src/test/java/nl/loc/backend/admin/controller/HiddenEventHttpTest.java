package nl.loc.backend.admin.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
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
@Sql(scripts = {"/db/publication-discoverable-events.sql", "/db/report.sql"},
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class HiddenEventHttpTest {

    private static final UUID ORGANIZER = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");

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
    private JwtEncoder jwtEncoder;
    @Autowired
    private JdbcClient jdbc;

    private UserAccount admin;
    private UserAccount member;

    @BeforeEach
    void setUp() {
        jdbc.sql("DELETE FROM app.report").update();
        jdbc.sql("DELETE FROM app.hidden_event").update();
        userRepository.deleteAll();
        jdbc.sql("DELETE FROM publication.event_snapshot").update();
        jdbc.sql("DELETE FROM publication.organizer_identity").update();
        admin = userRepository.save(new UserAccount("ada@example.com", "Ada", "hash", Instant.now()));
        member = userRepository.save(new UserAccount("alice@example.com", "Alice", "hash", Instant.now()));
        knownOrganizer(ORGANIZER);
    }

    @Test
    void hideStoresTheShowAndLeavesThePublicationRowUntouched() throws Exception {
        UUID showId = UUID.randomUUID();
        publish(showId, showId, "Secret concert", "PHYSICAL", "Rotterdam");

        perform(hide(showId, "  Duplicate listing  "), adminToken()).andExpect(status().isNoContent());

        assertThat(hiddenShowIds()).containsExactly(showId);
        assertThat(hiddenReason(showId)).isEqualTo("Duplicate listing");
        assertThat(hiddenBy(showId)).isEqualTo(admin.getId());
        assertThat(publicationTitle(showId)).isEqualTo("Secret concert");
        assertThat(stillDiscoverable(showId)).isTrue();
        perform(get("/api/admin/hidden-events"), adminToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eventId").value(showId.toString()))
                .andExpect(jsonPath("$[0].hiddenBy").value(admin.getId().toString()))
                .andExpect(jsonPath("$[0].hiddenByName").value("Ada"))
                .andExpect(jsonPath("$[0].reason").value("Duplicate listing"))
                .andExpect(jsonPath("$[0].hiddenAt").isNotEmpty());
    }

    @Test
    void hideThroughAnOldEventIdStoresTheShowId() throws Exception {
        UUID showId = UUID.randomUUID();
        UUID oldEventId = UUID.randomUUID();
        publish(showId, oldEventId, "Secret concert", "ONLINE", null);

        mockMvc.perform(get("/api/events/{id}", oldEventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(showId.toString()));

        perform(hide(oldEventId, "Wrong venue"), adminToken()).andExpect(status().isNoContent());

        assertThat(hiddenShowIds()).containsExactly(showId);
        mockMvc.perform(get("/api/events/{id}", showId)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/events/{id}", oldEventId)).andExpect(status().isNotFound());
    }

    @Test
    void hidingTwiceKeepsTheFirstRow() throws Exception {
        UUID showId = UUID.randomUUID();
        publish(showId, showId, "Secret concert", "ONLINE", null);

        perform(hide(showId, "First reason"), adminToken()).andExpect(status().isNoContent());
        perform(hide(showId, "Second reason"), adminToken()).andExpect(status().isNoContent());

        assertThat(hiddenShowIds()).containsExactly(showId);
        assertThat(hiddenReason(showId)).isEqualTo("First reason");
    }

    @Test
    void unknownEventAnswersNotFoundAndUnhidingAVisibleEventChangesNothing() throws Exception {
        UUID showId = UUID.randomUUID();
        publish(showId, showId, "Canal concert", "PHYSICAL", "Amsterdam");

        perform(hide(UUID.randomUUID(), "Gone"), adminToken()).andExpect(status().isNotFound());
        perform(delete("/api/admin/events/{id}/hide", UUID.randomUUID()), adminToken())
                .andExpect(status().isNotFound());
        perform(delete("/api/admin/events/{id}/hide", showId), adminToken()).andExpect(status().isNoContent());

        assertThat(hiddenShowIds()).isEmpty();
        mockMvc.perform(get("/api/events/{id}", showId)).andExpect(status().isOk());
    }

    @Test
    void blankOrTooLongReasonAnswersBadRequest() throws Exception {
        UUID showId = UUID.randomUUID();
        publish(showId, showId, "Secret concert", "ONLINE", null);

        perform(hide(showId, "   "), adminToken()).andExpect(status().isBadRequest());
        perform(hide(showId, "a".repeat(1001)), adminToken()).andExpect(status().isBadRequest());
        perform(post("/api/admin/events/{id}/hide", showId), adminToken()).andExpect(status().isBadRequest());
        perform(post("/api/admin/events/not-a-uuid/hide").contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Because\"}"), adminToken()).andExpect(status().isBadRequest());

        assertThat(hiddenShowIds()).isEmpty();
    }

    @Test
    void hiddenEventLeavesPublicDiscoveryAndReturnsWhenUnhidden() throws Exception {
        UUID secret = UUID.randomUUID();
        UUID canal = UUID.randomUUID();
        UUID stream = UUID.randomUUID();
        publish(secret, secret, "Secret concert", "PHYSICAL", "Rotterdam");
        publish(canal, canal, "Canal concert", "PHYSICAL", "Amsterdam");
        publish(stream, stream, "Visible stream", "ONLINE", null);
        perform(put("/api/me/followed-organizers/{id}", ORGANIZER), memberToken()).andExpect(status().isNoContent());
        perform(put("/api/me/saved-events/{id}", secret), memberToken()).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/events")).andExpect(jsonPath("$.totalElements").value(3));
        mockMvc.perform(get("/api/events").param("q", "Secret"))
                .andExpect(jsonPath("$.items[0].id").value(secret.toString()));
        mockMvc.perform(get("/api/events").param("city", "rotterdam"))
                .andExpect(jsonPath("$.items[0].id").value(secret.toString()));
        mockMvc.perform(get("/api/home").param("city", "rotterdam"))
                .andExpect(content().string(containsString("Secret concert")));
        mockMvc.perform(get("/api/home"))
                .andExpect(content().string(containsString("Canal concert")))
                .andExpect(content().string(containsString("Visible stream")));
        mockMvc.perform(get("/api/cities").param("q", "rotterdam"))
                .andExpect(jsonPath("$[0].slug").value("rotterdam"));
        mockMvc.perform(get("/api/organizers/{id}", ORGANIZER))
                .andExpect(jsonPath("$.events.totalElements").value(3));
        perform(get("/api/me/followed-organizers/events"), memberToken())
                .andExpect(jsonPath("$.totalElements").value(3));
        mockMvc.perform(get("/api/events/{id}", secret)).andExpect(status().isOk());

        perform(hide(secret, "Not a real event"), adminToken()).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/events"))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(content().string(not(containsString("Secret concert"))));
        mockMvc.perform(get("/api/events").param("q", "Secret")).andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/events").param("city", "rotterdam")).andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/home").param("city", "rotterdam"))
                .andExpect(content().string(not(containsString("Secret concert"))))
                .andExpect(content().string(containsString("Visible stream")));
        mockMvc.perform(get("/api/home"))
                .andExpect(content().string(containsString("Canal concert")))
                .andExpect(content().string(not(containsString("Secret concert"))));
        mockMvc.perform(get("/api/cities").param("q", "rotterdam")).andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(get("/api/organizers/{id}", ORGANIZER))
                .andExpect(jsonPath("$.events.totalElements").value(2))
                .andExpect(content().string(not(containsString(secret.toString()))));
        perform(get("/api/me/followed-organizers/events"), memberToken())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(content().string(not(containsString(secret.toString()))));
        mockMvc.perform(get("/api/events/{id}", secret)).andExpect(status().isNotFound());
        perform(get("/api/me/saved-events"), memberToken())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].event.id").value(secret.toString()))
                .andExpect(jsonPath("$.items[0].event.title").value("Secret concert"));
        perform(get("/api/me/saved-events/{id}", secret), memberToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(secret.toString()))
                .andExpect(jsonPath("$.title").value("Secret concert"));
        assertThat(stillDiscoverable(secret)).isTrue();

        perform(delete("/api/admin/events/{id}/hide", secret), adminToken()).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/events/{id}", secret)).andExpect(status().isOk());
        mockMvc.perform(get("/api/events")).andExpect(jsonPath("$.totalElements").value(3));
        mockMvc.perform(get("/api/events").param("q", "Secret"))
                .andExpect(jsonPath("$.items[0].id").value(secret.toString()));
        mockMvc.perform(get("/api/home").param("city", "rotterdam"))
                .andExpect(content().string(containsString("Secret concert")));
        mockMvc.perform(get("/api/organizers/{id}", ORGANIZER))
                .andExpect(jsonPath("$.events.totalElements").value(3));
        perform(get("/api/me/followed-organizers/events"), memberToken())
                .andExpect(jsonPath("$.totalElements").value(3));
        assertThat(hiddenShowIds()).isEmpty();
    }

    @Test
    void anOrganizerWithOnlyHiddenEventsDisappears() throws Exception {
        UUID showId = UUID.randomUUID();
        publish(showId, showId, "Secret concert", "ONLINE", null);

        perform(hide(showId, "Spam"), adminToken()).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/organizers/{id}", ORGANIZER)).andExpect(status().isNotFound());
    }

    @Test
    void membersAreForbiddenAndSignedOutCallsAreUnauthorized() throws Exception {
        UUID showId = UUID.randomUUID();
        publish(showId, showId, "Secret concert", "ONLINE", null);

        mockMvc.perform(get("/api/admin/hidden-events")).andExpect(status().isUnauthorized());
        mockMvc.perform(hide(showId, "Spam")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/admin/events/{id}/hide", showId)).andExpect(status().isUnauthorized());
        perform(get("/api/admin/hidden-events"), memberToken()).andExpect(status().isForbidden());
        perform(hide(showId, "Spam"), memberToken()).andExpect(status().isForbidden());
        perform(delete("/api/admin/events/{id}/hide", showId), memberToken()).andExpect(status().isForbidden());

        assertThat(hiddenShowIds()).isEmpty();
    }

    @Test
    void hidingAfterTheAccountWasDeletedAnswersUnauthorized() throws Exception {
        UUID showId = UUID.randomUUID();
        publish(showId, showId, "Secret concert", "ONLINE", null);
        String token = adminToken();
        userRepository.delete(admin);

        perform(hide(showId, "Spam"), token).andExpect(status().isUnauthorized());
        perform(delete("/api/admin/events/{id}/hide", showId), token).andExpect(status().isUnauthorized());

        assertThat(hiddenShowIds()).isEmpty();
    }

    private String adminToken() {
        Instant expiresAt = Instant.now().plus(Duration.ofMinutes(5));
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(admin.getId().toString())
                .issuedAt(expiresAt.minus(Duration.ofMinutes(1)))
                .expiresAt(expiresAt)
                .claim("roles", List.of("ADMIN"))
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    private String memberToken() {
        return accessTokenService.issueAccessToken(member);
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, String token) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private static MockHttpServletRequestBuilder hide(UUID eventId, String reason) {
        return post("/api/admin/events/{eventId}/hide", eventId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"" + reason + "\"}");
    }

    private List<UUID> hiddenShowIds() {
        return jdbc.sql("SELECT event_id FROM app.hidden_event ORDER BY event_id").query(UUID.class).list();
    }

    private String hiddenReason(UUID showId) {
        return jdbc.sql("SELECT reason FROM app.hidden_event WHERE event_id = :id")
                .param("id", showId)
                .query(String.class)
                .single();
    }

    private UUID hiddenBy(UUID showId) {
        return jdbc.sql("SELECT hidden_by FROM app.hidden_event WHERE event_id = :id")
                .param("id", showId)
                .query(UUID.class)
                .single();
    }

    private String publicationTitle(UUID showId) {
        return jdbc.sql("SELECT payload->>'title' FROM publication.event_snapshot WHERE loc_group_id = :id")
                .param("id", showId)
                .query(String.class)
                .single();
    }

    private boolean stillDiscoverable(UUID showId) {
        return jdbc.sql("SELECT discoverable FROM publication.event_snapshot WHERE loc_group_id = :id")
                .param("id", showId)
                .query(Boolean.class)
                .single();
    }

    private void knownOrganizer(UUID organizerId) {
        jdbc.sql("""
                INSERT INTO publication.organizer_identity (loc_organizer_id, source, external_id)
                VALUES (:id, 'test', :externalId)
                """)
                .param("id", organizerId)
                .param("externalId", organizerId.toString())
                .update();
    }

    private void publish(UUID showId, UUID eventId, String title, String place, String city) {
        String location = city == null
                ? "{\"type\": \"" + place + "\"}"
                : "{\"type\": \"" + place + "\", \"city\": \"" + city + "\", \"venue_name\": \"Hall\"}";
        String organizers = "{\"locOrganizerId\": \"" + ORGANIZER + "\", \"name\": \"Organizer\"}";
        jdbc.sql("""
                INSERT INTO publication.event_snapshot (
                    loc_occurrence_id, loc_event_id, loc_group_id, revision, discoverable, state, payload,
                    first_published_at, updated_at
                ) VALUES (
                    :occurrenceId, :eventId, :showId, 1, true, 'PUBLISHED',
                    jsonb_build_object(
                        'title', :title,
                        'location', CAST(:location AS jsonb),
                        'schedule', jsonb_build_object(
                            'starts_at', '2998-06-01T18:00:00Z', 'ends_at', '2998-06-01T21:00:00Z'),
                        'validUntil', '2999-01-01T00:00:00Z',
                        'organizers', CAST(:organizers AS jsonb)),
                    now(), now()
                )
                """)
                .param("occurrenceId", UUID.randomUUID())
                .param("eventId", eventId)
                .param("showId", showId)
                .param("title", title)
                .param("location", location)
                .param("organizers", "[" + organizers + "]")
                .update();
    }
}
