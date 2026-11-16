package nl.loc.backend.admin.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
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
class AdminReportHttpTest {

    private static final OffsetDateTime DAY_ONE = OffsetDateTime.parse("2026-01-01T00:00:00Z");
    private static final OffsetDateTime DAY_TWO = OffsetDateTime.parse("2026-01-02T00:00:00Z");
    private static final OffsetDateTime DAY_THREE = OffsetDateTime.parse("2026-01-03T00:00:00Z");

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
    private UserAccount alice;
    private UserAccount bob;

    @BeforeEach
    void setUp() {
        jdbc.sql("DELETE FROM app.report").update();
        jdbc.sql("DELETE FROM app.hidden_event").update();
        userRepository.deleteAll();
        jdbc.sql("DELETE FROM publication.event_snapshot").update();
        admin = userRepository.save(new UserAccount("ada@example.com", "Ada", "hash", Instant.now()));
        alice = userRepository.save(new UserAccount("alice@example.com", "Alice", "hash", Instant.now()));
        bob = userRepository.save(new UserAccount("bob@example.com", "Bob", "hash", Instant.now()));
    }

    @Test
    void openReportsAreGroupedPerItemWithCountReasonsAndIds() throws Exception {
        UUID showId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();
        UUID older = report("EVENT", showId, alice, "INAPPROPRIATE", DAY_TWO);
        UUID newer = report("EVENT", showId, bob, "SPAM", DAY_THREE);
        UUID comment = report("COMMENT", commentId, alice, "OTHER", DAY_ONE);
        report("EVENT", showId, admin, "SPAM", DAY_ONE, "DISMISSED", admin);

        perform(get("/api/admin/reports"), adminToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].targetType").value("EVENT"))
                .andExpect(jsonPath("$[0].targetId").value(showId.toString()))
                .andExpect(jsonPath("$[0].openCount").value(2))
                .andExpect(jsonPath("$[0].reasons", contains("INAPPROPRIATE", "SPAM")))
                .andExpect(jsonPath("$[0].reportIds", contains(older.toString(), newer.toString())))
                .andExpect(jsonPath("$[1].targetType").value("COMMENT"))
                .andExpect(jsonPath("$[1].targetId").value(commentId.toString()))
                .andExpect(jsonPath("$[1].openCount").value(1))
                .andExpect(jsonPath("$[1].reasons", contains("OTHER")))
                .andExpect(jsonPath("$[1].reportIds", contains(comment.toString())));
    }

    @Test
    void groupsCarryTheEventTitleAndTheReportedCommentWithItsAuthorAndEvent() throws Exception {
        UUID showId = UUID.randomUUID();
        publish(showId, showId);
        hideDirectly(showId);
        UUID live = comment(showId, alice, "Buy cheap tickets here", false);
        UUID removed = comment(showId, bob, "Rude words", true);
        UUID missing = UUID.randomUUID();
        report("EVENT", showId, alice, "SPAM", DAY_THREE);
        report("COMMENT", live, bob, "SPAM", DAY_TWO);
        report("COMMENT", removed, alice, "INAPPROPRIATE", DAY_ONE);
        report("COMMENT", missing, bob, "OTHER", DAY_ONE.minusDays(1));

        perform(get("/api/admin/reports"), adminToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].eventId").value(showId.toString()))
                .andExpect(jsonPath("$[0].eventTitle").value("Reported"))
                .andExpect(jsonPath("$[0].eventHidden").value(true))
                .andExpect(jsonPath("$[0].commentBody").doesNotExist())
                .andExpect(jsonPath("$[0].commentAuthorName").doesNotExist())
                .andExpect(jsonPath("$[1].targetId").value(live.toString()))
                .andExpect(jsonPath("$[1].commentBody").value("Buy cheap tickets here"))
                .andExpect(jsonPath("$[1].commentAuthorName").value("Alice"))
                .andExpect(jsonPath("$[1].eventId").value(showId.toString()))
                .andExpect(jsonPath("$[1].eventTitle").value("Reported"))
                .andExpect(jsonPath("$[2].targetId").value(removed.toString()))
                .andExpect(jsonPath("$[2].commentBody").doesNotExist())
                .andExpect(jsonPath("$[2].commentAuthorName").value("Bob"))
                .andExpect(jsonPath("$[3].targetId").value(missing.toString()))
                .andExpect(jsonPath("$[3].eventId").doesNotExist())
                .andExpect(jsonPath("$[3].eventTitle").doesNotExist())
                .andExpect(jsonPath("$[3].eventHidden").value(false));
    }

    @Test
    void anEmptyQueueIsAnEmptyList() throws Exception {
        perform(get("/api/admin/reports"), adminToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void dismissClosesTheReportWithoutAction() throws Exception {
        UUID showId = UUID.randomUUID();
        UUID kept = report("EVENT", showId, alice, "SPAM", DAY_ONE);
        UUID dismissed = report("EVENT", showId, bob, "OTHER", DAY_TWO);

        perform(post("/api/admin/reports/{id}/dismiss", dismissed), adminToken()).andExpect(status().isNoContent());

        assertThat(resolution(dismissed)).isEqualTo("DISMISSED");
        assertThat(resolvedBy(dismissed)).isEqualTo(admin.getId());
        assertThat(isResolved(dismissed)).isTrue();
        assertThat(isResolved(kept)).isFalse();
        perform(get("/api/admin/reports"), adminToken())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].openCount").value(1))
                .andExpect(jsonPath("$[0].reportIds", contains(kept.toString())))
                .andExpect(jsonPath("$[0].reasons", contains("SPAM")));
    }

    @Test
    void dismissingAnUnknownOrResolvedReportAnswersNotFound() throws Exception {
        UUID showId = UUID.randomUUID();
        UUID dismissed = report("COMMENT", showId, alice, "SPAM", DAY_ONE, "DISMISSED", alice);

        perform(post("/api/admin/reports/{id}/dismiss", UUID.randomUUID()), adminToken())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Report not found."));
        perform(post("/api/admin/reports/{id}/dismiss", dismissed), adminToken())
                .andExpect(status().isNotFound());

        assertThat(resolution(dismissed)).isEqualTo("DISMISSED");
        assertThat(resolvedBy(dismissed)).isEqualTo(alice.getId());
    }

    @Test
    void hidingResolvesOpenEventReportsAndUnhidingDoesNotReopenThem() throws Exception {
        UUID showId = UUID.randomUUID();
        UUID oldEventId = UUID.randomUUID();
        UUID otherShow = UUID.randomUUID();
        publish(showId, oldEventId);
        publish(otherShow, otherShow);
        UUID open = report("EVENT", showId, alice, "SPAM", DAY_ONE);
        UUID filedOnOldId = report("EVENT", oldEventId, bob, "WRONG_INFORMATION", DAY_TWO);
        UUID comment = report("COMMENT", UUID.randomUUID(), alice, "INAPPROPRIATE", DAY_ONE);
        UUID other = report("EVENT", otherShow, alice, "OTHER", DAY_ONE);
        UUID already = report("EVENT", showId, bob, "SPAM", DAY_ONE, "DISMISSED", alice);

        perform(hide(oldEventId, "Spam listing"), adminToken()).andExpect(status().isNoContent());

        assertThat(resolution(open)).isEqualTo("HIDDEN");
        assertThat(resolvedBy(open)).isEqualTo(admin.getId());
        assertThat(isResolved(open)).isTrue();
        assertThat(isResolved(filedOnOldId)).isFalse();
        assertThat(isResolved(comment)).isFalse();
        assertThat(isResolved(other)).isFalse();
        assertThat(resolution(already)).isEqualTo("DISMISSED");
        assertThat(resolvedBy(already)).isEqualTo(alice.getId());

        UUID filedAfterHide = report("EVENT", showId, bob, "OTHER", DAY_THREE);
        perform(hide(showId, "Still spam"), adminToken()).andExpect(status().isNoContent());
        assertThat(resolution(filedAfterHide)).isEqualTo("HIDDEN");
        assertThat(jdbc.sql("SELECT count(*) FROM app.hidden_event").query(Long.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("SELECT reason FROM app.hidden_event").query(String.class).single()).isEqualTo("Spam listing");

        perform(delete("/api/admin/events/{id}/hide", showId), adminToken()).andExpect(status().isNoContent());

        assertThat(resolution(open)).isEqualTo("HIDDEN");
        assertThat(isResolved(open)).isTrue();
        assertThat(isResolved(comment)).isFalse();
        perform(get("/api/admin/reports"), adminToken())
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void membersAreForbiddenAndSignedOutCallsAreUnauthorized() throws Exception {
        UUID reportId = report("EVENT", UUID.randomUUID(), alice, "SPAM", DAY_ONE);

        mockMvc.perform(get("/api/admin/reports")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/admin/reports/{id}/dismiss", reportId)).andExpect(status().isUnauthorized());
        perform(get("/api/admin/reports"), memberToken()).andExpect(status().isForbidden());
        perform(post("/api/admin/reports/{id}/dismiss", reportId), memberToken()).andExpect(status().isForbidden());

        assertThat(isResolved(reportId)).isFalse();
    }

    @Test
    void dismissingAfterTheAccountWasDeletedAnswersUnauthorized() throws Exception {
        UUID reportId = report("EVENT", UUID.randomUUID(), alice, "SPAM", DAY_ONE);
        String token = adminToken();
        userRepository.delete(admin);

        perform(post("/api/admin/reports/{id}/dismiss", reportId), token).andExpect(status().isUnauthorized());

        assertThat(isResolved(reportId)).isFalse();
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
        return accessTokenService.issueAccessToken(alice);
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, String token) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private static MockHttpServletRequestBuilder hide(UUID eventId, String reason) {
        return post("/api/admin/events/{eventId}/hide", eventId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"" + reason + "\"}");
    }

    private UUID report(String targetType, UUID targetId, UserAccount reporter, String reason,
                        OffsetDateTime createdAt) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO app.report (id, target_type, target_id, reporter_id, reason, created_at)
                VALUES (:id, :targetType, :targetId, :reporterId, :reason, :createdAt)
                """)
                .param("id", id)
                .param("targetType", targetType)
                .param("targetId", targetId)
                .param("reporterId", reporter.getId())
                .param("reason", reason)
                .param("createdAt", createdAt)
                .update();
        return id;
    }

    private UUID report(String targetType, UUID targetId, UserAccount reporter, String reason, OffsetDateTime createdAt,
                        String resolution, UserAccount resolvedBy) {
        UUID id = report(targetType, targetId, reporter, reason, createdAt);
        jdbc.sql("""
                UPDATE app.report
                SET resolved_at = :resolvedAt, resolved_by = :resolvedBy, resolution = :resolution
                WHERE id = :id
                """)
                .param("id", id)
                .param("resolvedAt", DAY_THREE)
                .param("resolvedBy", resolvedBy.getId())
                .param("resolution", resolution)
                .update();
        return id;
    }

    private String resolution(UUID id) {
        return jdbc.sql("SELECT resolution FROM app.report WHERE id = :id")
                .param("id", id)
                .query(String.class)
                .single();
    }

    private UUID resolvedBy(UUID id) {
        return jdbc.sql("SELECT resolved_by FROM app.report WHERE id = :id")
                .param("id", id)
                .query(UUID.class)
                .single();
    }

    private boolean isResolved(UUID id) {
        return jdbc.sql("SELECT resolved_at IS NOT NULL FROM app.report WHERE id = :id")
                .param("id", id)
                .query(Boolean.class)
                .single();
    }

    private UUID comment(UUID showId, UserAccount author, String body, boolean removed) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO app.comment (id, event_id, user_id, body, deleted_at, deleted_by, delete_reason)
                VALUES (:id, :eventId, :userId, :body,
                        CASE WHEN :removed THEN now() END,
                        CASE WHEN :removed THEN CAST(:adminId AS uuid) END,
                        CASE WHEN :removed THEN 'Abuse' END)
                """)
                .param("id", id)
                .param("eventId", showId)
                .param("userId", author.getId())
                .param("body", body)
                .param("removed", removed)
                .param("adminId", admin.getId())
                .update();
        return id;
    }

    private void hideDirectly(UUID showId) {
        jdbc.sql("""
                INSERT INTO app.hidden_event (event_id, hidden_by, reason, hidden_at)
                VALUES (:eventId, :adminId, 'Duplicate', now())
                """)
                .param("eventId", showId)
                .param("adminId", admin.getId())
                .update();
    }

    private void publish(UUID showId, UUID eventId) {
        jdbc.sql("""
                INSERT INTO publication.event_snapshot (
                    loc_occurrence_id, loc_event_id, loc_group_id, revision, discoverable, state, payload,
                    first_published_at, updated_at
                ) VALUES (
                    :occurrenceId, :eventId, :showId, 1, true, 'PUBLISHED',
                    CAST('{"title": "Reported", "location": {"type": "ONLINE"},
                           "schedule": {"starts_at": "2998-06-01T18:00:00Z"},
                           "validUntil": "2999-01-01T00:00:00Z"}' AS jsonb),
                    now(), now()
                )
                """)
                .param("occurrenceId", UUID.randomUUID())
                .param("eventId", eventId)
                .param("showId", showId)
                .update();
    }
}
