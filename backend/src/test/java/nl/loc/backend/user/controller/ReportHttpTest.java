package nl.loc.backend.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.util.UUID;
import nl.loc.backend.user.model.UserAccount;
import nl.loc.backend.user.repository.UserAccountRepository;
import nl.loc.backend.user.service.AccessTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.NestedTestConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
// nothing listens on port 1, so the rate limiter fails open and never limits these tests
@TestPropertySource(properties = "spring.data.redis.url=redis://localhost:1")
@Sql(scripts = "/db/publication-discoverable-events.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class ReportHttpTest {

    private static final UUID SHOW_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");

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
        jdbc.sql("DELETE FROM app.report").update();
        jdbc.sql("DELETE FROM app.comment").update();
        userRepository.deleteAll();
        jdbc.sql("DELETE FROM publication.event_snapshot").update();
        alice = userRepository.save(new UserAccount("alice@example.com", "Alice", "hash", Instant.now()));
        bob = userRepository.save(new UserAccount("bob@example.com", "Bob", "hash", Instant.now()));
        publish(SHOW_ID, SHOW_ID, true, "UPCOMING");
    }

    @Test
    void reportingAnEventAnswersCreatedWithoutAnyAdminDecision() throws Exception {
        reportEvent(token(alice), SHOW_ID, "WRONG_INFORMATION", "The date is wrong")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.targetType").value("EVENT"))
                .andExpect(jsonPath("$.targetId").value(SHOW_ID.toString()))
                .andExpect(jsonPath("$.reason").value("WRONG_INFORMATION"))
                .andExpect(jsonPath("$.details").value("The date is wrong"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(content().string(not(containsString("resolved"))))
                .andExpect(content().string(not(containsString("resolution"))))
                .andExpect(content().string(not(containsString("\"status\""))));

        assertThat(reportCount()).isEqualTo(1);
    }

    @Test
    void blankDetailsAreStoredAsNullAndTextIsTrimmed() throws Exception {
        reportEvent(token(alice), SHOW_ID, "SPAM", "   ")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.details").value(nullValue()));
        assertThat(jdbc.sql("SELECT details IS NULL FROM app.report").query(Boolean.class).single()).isTrue();

        jdbc.sql("DELETE FROM app.report").update();
        reportEvent(token(alice), SHOW_ID, "OTHER", "  please check  ")
                .andExpect(jsonPath("$.details").value("please check"));
    }

    @Test
    void reportingTheSameEventAgainReturnsTheOpenReport() throws Exception {
        String first = reportEvent(token(alice), SHOW_ID, "SPAM", "Posted twice")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String firstId = JsonPath.read(first, "$.id");

        reportEvent(token(alice), SHOW_ID, "INAPPROPRIATE", "A different reason")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(firstId))
                .andExpect(jsonPath("$.reason").value("SPAM"))
                .andExpect(jsonPath("$.details").value("Posted twice"));

        assertThat(reportCount()).isEqualTo(1);
        reportEvent(token(bob), SHOW_ID, "OTHER", null).andExpect(status().isCreated());
        assertThat(reportCount()).isEqualTo(2);
    }

    @Test
    void anOldEventIdReportsTheShow() throws Exception {
        UUID show = UUID.randomUUID();
        UUID oldEventId = UUID.randomUUID();
        publish(show, oldEventId, true, "UPCOMING");

        reportEvent(token(alice), oldEventId, "WRONG_INFORMATION", null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.targetId").value(show.toString()));

        reportEvent(token(alice), show, "SPAM", "again")
                .andExpect(jsonPath("$.reason").value("WRONG_INFORMATION"));
        assertThat(reportCount()).isEqualTo(1);
    }

    @Test
    void aHiddenEventCanStillBeReported() throws Exception {
        UUID hidden = UUID.randomUUID();
        publish(hidden, hidden, false, "UNAVAILABLE");

        reportEvent(token(alice), hidden, "INAPPROPRIATE", null).andExpect(status().isCreated());
        assertThat(reportCount()).isEqualTo(1);
    }

    @Test
    void unknownEventAnswersNotFound() throws Exception {
        reportEvent(token(alice), UUID.randomUUID(), "SPAM", null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Event not found."));

        assertThat(reportCount()).isZero();
    }

    @Test
    void reportingACommentAnswersCreated() throws Exception {
        UUID comment = comment(alice, "Is there parking?");

        reportComment(token(bob), comment, "SPAM", "Not about the event")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.targetType").value("COMMENT"))
                .andExpect(jsonPath("$.targetId").value(comment.toString()))
                .andExpect(jsonPath("$.reason").value("SPAM"))
                .andExpect(jsonPath("$.details").value("Not about the event"));
    }

    @Test
    void reportingTheSameCommentAgainReturnsTheOpenReport() throws Exception {
        UUID comment = comment(alice, "Is there parking?");
        String first = reportComment(token(bob), comment, "INAPPROPRIATE", "Rude")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String firstId = JsonPath.read(first, "$.id");

        reportComment(token(bob), comment, "SPAM", "Changed my mind")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(firstId))
                .andExpect(jsonPath("$.reason").value("INAPPROPRIATE"));

        assertThat(reportCount()).isEqualTo(1);
    }

    @Test
    void unknownOrDeletedCommentAnswersNotFound() throws Exception {
        UUID comment = comment(alice, "Is there parking?");
        perform(delete("/api/me/comments/{id}", comment), token(alice)).andExpect(status().isNoContent());

        reportComment(token(bob), comment, "SPAM", null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Comment not found."));
        reportComment(token(bob), UUID.randomUUID(), "SPAM", null).andExpect(status().isNotFound());

        assertThat(reportCount()).isZero();
    }

    @Test
    void signedOutAnswersUnauthorized() throws Exception {
        UUID comment = comment(alice, "Is there parking?");

        mockMvc.perform(reportBody(post("/api/events/{id}/reports", SHOW_ID), "SPAM", null))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(reportBody(post("/api/comments/{id}/reports", comment), "SPAM", null))
                .andExpect(status().isUnauthorized());

        assertThat(reportCount()).isZero();
    }

    @Test
    void missingReasonOrTooLongDetailsAnswerBadRequest() throws Exception {
        mockMvc.perform(post("/api/events/{id}/reports", SHOW_ID)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(alice))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"details\": \"No reason\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/events/{id}/reports", SHOW_ID)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(alice))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"NOT_A_REASON\"}"))
                .andExpect(status().isBadRequest());
        reportEvent(token(alice), SHOW_ID, "OTHER", "a".repeat(1001)).andExpect(status().isBadRequest());

        assertThat(reportCount()).isZero();
    }

    @Test
    void reportingAfterTheAccountWasDeletedAnswersUnauthorized() throws Exception {
        String token = token(alice);
        userRepository.delete(alice);

        reportEvent(token, SHOW_ID, "SPAM", null).andExpect(status().isUnauthorized());
        assertThat(reportCount()).isZero();
    }

    @Test
    void aResolvedReportDoesNotBlockANewOne() throws Exception {
        String firstId = JsonPath.read(reportEvent(token(alice), SHOW_ID, "SPAM", "First")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
        jdbc.sql("""
                UPDATE app.report
                SET resolved_at = now(), resolved_by = :resolver, resolution = 'DISMISSED'
                WHERE id = :id
                """)
                .param("resolver", alice.getId())
                .param("id", UUID.fromString(firstId))
                .update();

        reportEvent(token(alice), SHOW_ID, "OTHER", "Still wrong")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(not(firstId)))
                .andExpect(jsonPath("$.reason").value("OTHER"));

        assertThat(reportCount()).isEqualTo(2);
    }

    @Test
    void partialUniqueIndexRejectsASecondOpenReport() throws Exception {
        reportEvent(token(alice), SHOW_ID, "SPAM", "Once").andExpect(status().isCreated());

        String predicate = jdbc.sql("""
                SELECT pg_get_expr(i.indpred, i.indrelid)
                FROM pg_index i
                JOIN pg_class c ON c.oid = i.indexrelid
                JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE n.nspname = 'app'
                  AND c.relname = 'report_open_per_member_idx'
                  AND i.indisunique
                """).query(String.class).single();
        assertThat(predicate.replace("(", "").replace(")", "")).isEqualToIgnoringCase("resolved_at IS NULL");

        String definition = jdbc.sql("""
                SELECT pg_get_indexdef(c.oid)
                FROM pg_class c
                JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE n.nspname = 'app' AND c.relname = 'report_open_per_member_idx'
                """).query(String.class).single();
        assertThat(definition).contains("(target_type, target_id, reporter_id)");

        assertThatThrownBy(() -> jdbc.sql("""
                INSERT INTO app.report (target_type, target_id, reporter_id, reason)
                SELECT target_type, target_id, reporter_id, reason FROM app.report
                """).update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("report_open_per_member_idx");

        assertThat(reportCount()).isEqualTo(1);
    }

    private UUID comment(UserAccount user, String body) throws Exception {
        String json = perform(post("/api/events/{id}/comments", SHOW_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\": \"" + body + "\"}"), token(user))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(json, "$.id"));
    }

    private ResultActions reportEvent(String token, UUID eventId, String reason, String details) throws Exception {
        return perform(reportBody(post("/api/events/{id}/reports", eventId), reason, details), token);
    }

    private ResultActions reportComment(String token, UUID commentId, String reason, String details) throws Exception {
        return perform(reportBody(post("/api/comments/{id}/reports", commentId), reason, details), token);
    }

    private String token(UserAccount user) {
        return accessTokenService.issueAccessToken(user);
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, String token) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private static MockHttpServletRequestBuilder reportBody(MockHttpServletRequestBuilder request, String reason,
                                                            String details) {
        String json = details == null
                ? "{\"reason\": \"" + reason + "\"}"
                : "{\"reason\": \"" + reason + "\", \"details\": \"" + details + "\"}";
        return request.contentType(MediaType.APPLICATION_JSON).content(json);
    }

    private long reportCount() {
        return jdbc.sql("SELECT count(*) FROM app.report").query(Long.class).single();
    }

    private void publish(UUID showId, UUID eventId, boolean discoverable, String state) {
        jdbc.sql("""
                INSERT INTO publication.event_snapshot (
                    loc_occurrence_id, loc_event_id, loc_group_id, revision, discoverable, state, payload,
                    first_published_at, updated_at
                ) VALUES (
                    :occurrenceId, :eventId, :showId, 1, :discoverable, :state,
                    CAST('{"title": "Late night jazz", "location": {"type": "ONLINE"},
                           "schedule": {"starts_at": "2998-01-01T20:00:00Z"},
                           "validUntil": "2999-01-01T00:00:00Z"}' AS jsonb), now(), now()
                )
                """)
                .param("occurrenceId", UUID.randomUUID())
                .param("eventId", eventId)
                .param("showId", showId)
                .param("discoverable", discoverable)
                .param("state", state)
                .update();
    }

    @Nested
    @SpringBootTest(properties = {
            "app.rate-limit.policies.report.capacity=2",
            "app.rate-limit.policies.report.period=1h"})
    @AutoConfigureMockMvc
    @Testcontainers
    @NestedTestConfiguration(NestedTestConfiguration.EnclosingConfiguration.OVERRIDE)
    @Sql(scripts = "/db/publication-discoverable-events.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
    class ReportRateLimit {

        private static final UUID SHOW_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
        private static final UUID OTHER_SHOW = UUID.fromString("33333333-3333-4333-8333-333333333333");

        @Container
        @ServiceConnection
        private static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

        private static final GenericContainer<?> redis = new GenericContainer<>("redis:8.2-alpine")
                .withExposedPorts(6379);

        static {
            // @Sql before the class loads the context before @Container would start Redis, so start it here
            redis.start();
        }

        @DynamicPropertySource
        static void redisUrl(DynamicPropertyRegistry registry) {
            registry.add("spring.data.redis.url",
                    () -> "redis://" + redis.getHost() + ":" + redis.getMappedPort(6379));
        }

        @Autowired
        private MockMvc mockMvc;
        @Autowired
        private UserAccountRepository userRepository;
        @Autowired
        private AccessTokenService accessTokenService;
        @Autowired
        private JdbcClient jdbc;

        @Test
        void tooManyReportsAnswerTooManyRequestsPerMember() throws Exception {
            jdbc.sql("DELETE FROM app.report").update();
            userRepository.deleteAll();
            jdbc.sql("DELETE FROM publication.event_snapshot").update();
            publish(SHOW_ID);
            publish(OTHER_SHOW);
            String alice = token(userRepository.save(
                    new UserAccount("rate-alice@example.com", "Alice", "hash", Instant.now())));
            String bob = token(userRepository.save(
                    new UserAccount("rate-bob@example.com", "Bob", "hash", Instant.now())));

            report(alice, SHOW_ID).andExpect(status().isCreated());
            report(alice, SHOW_ID).andExpect(status().isCreated());
            report(alice, OTHER_SHOW)
                    .andExpect(status().isTooManyRequests())
                    .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
            report(bob, OTHER_SHOW).andExpect(status().isCreated());

            assertThat(jdbc.sql("SELECT count(*) FROM app.report").query(Long.class).single()).isEqualTo(2);
        }

        private ResultActions report(String token, UUID eventId) throws Exception {
            return mockMvc.perform(post("/api/events/{id}/reports", eventId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"reason\": \"SPAM\"}"));
        }

        private String token(UserAccount user) {
            return accessTokenService.issueAccessToken(user);
        }

        private void publish(UUID id) {
            jdbc.sql("""
                    INSERT INTO publication.event_snapshot (
                        loc_occurrence_id, loc_event_id, loc_group_id, revision, discoverable, state, payload,
                        first_published_at, updated_at
                    ) VALUES (gen_random_uuid(), :id, :id, 1, true, 'UPCOMING', '{}'::jsonb, now(), now())
                    """).param("id", id).update();
        }
    }
}
