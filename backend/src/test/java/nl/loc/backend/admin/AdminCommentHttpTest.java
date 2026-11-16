package nl.loc.backend.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
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
class AdminCommentHttpTest {

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
    private JwtEncoder jwtEncoder;
    @Autowired
    private JdbcClient jdbc;

    private UserAccount admin;
    private UserAccount alice;
    private UserAccount bob;

    @BeforeEach
    void setUp() {
        jdbc.sql("DELETE FROM app.report").update();
        jdbc.sql("DELETE FROM app.comment").update();
        userRepository.deleteAll();
        jdbc.sql("DELETE FROM publication.event_snapshot").update();
        admin = userRepository.save(new UserAccount("ada@example.com", "Ada", "hash", Instant.now()));
        alice = userRepository.save(new UserAccount("alice@example.com", "Alice", "hash", Instant.now()));
        bob = userRepository.save(new UserAccount("bob@example.com", "Bob", "hash", Instant.now()));
        publish(SHOW_ID, SHOW_ID, true, "UPCOMING");
    }

    @Test
    void adminRemovesACommentAndStoresWhoAndWhy() throws Exception {
        UUID comment = comment(alice, "Is there parking?");

        remove(comment, "  Spam and off topic  ").andExpect(status().isNoContent());

        assertThat(deletedBy(comment)).isEqualTo(admin.getId());
        assertThat(deleteReason(comment)).isEqualTo("Spam and off topic");
        assertThat(commentCount()).isEqualTo(1);
    }

    @Test
    void removedCommentTextIsAbsentFromPublicComments() throws Exception {
        UUID comment = comment(alice, "Secret moderation text");
        reply(bob, comment, "A reply that stays");

        remove(comment, "Inappropriate").andExpect(status().isNoContent());

        mockMvc.perform(get("/api/events/{id}/comments", SHOW_ID))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Secret moderation text"))))
                .andExpect(content().string(not(containsString("Inappropriate"))))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].deleted").value(true))
                .andExpect(jsonPath("$.items[0].body").doesNotExist())
                .andExpect(jsonPath("$.items[0].author").doesNotExist())
                .andExpect(jsonPath("$.items[0].replies[0].body").value("A reply that stays"));
    }

    @Test
    void removedCommentWithoutRepliesIsLeftOut() throws Exception {
        comment(alice, "Never mind");

        remove(commentIdOfOnlyComment(), "Spam").andExpect(status().isNoContent());

        mockMvc.perform(get("/api/events/{id}/comments", SHOW_ID))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Never mind"))))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void removingACommentResolvesItsOpenCommentReports() throws Exception {
        UUID comment = comment(alice, "Buy followers");
        UUID other = comment(alice, "A real question");
        UUID open = report("COMMENT", comment, bob, null);
        UUID alreadyResolved = report("COMMENT", comment, bob, "DISMISSED");
        UUID eventReport = report("EVENT", comment, bob, null);
        UUID otherComment = report("COMMENT", other, bob, null);

        remove(comment, "Spam").andExpect(status().isNoContent());

        assertThat(resolution(open)).isEqualTo("REMOVED");
        assertThat(resolvedBy(open)).isEqualTo(admin.getId());
        assertThat(isResolved(open)).isTrue();
        assertThat(resolution(alreadyResolved)).isEqualTo("DISMISSED");
        assertThat(resolvedBy(alreadyResolved)).isEqualTo(bob.getId());
        assertThat(resolution(eventReport)).isNull();
        assertThat(isResolved(eventReport)).isFalse();
        assertThat(resolution(otherComment)).isNull();
        assertThat(isResolved(otherComment)).isFalse();
    }

    @Test
    void removingAnAlreadyRemovedCommentChangesNothing() throws Exception {
        UUID comment = comment(alice, "Is there parking?");
        remove(comment, "Spam").andExpect(status().isNoContent());
        Instant removedAt = deletedAt(comment);
        UUID openAfter = report("COMMENT", comment, bob, null);

        remove(comment, "A different reason").andExpect(status().isNoContent());

        assertThat(deletedBy(comment)).isEqualTo(admin.getId());
        assertThat(deleteReason(comment)).isEqualTo("Spam");
        assertThat(deletedAt(comment)).isEqualTo(removedAt);
        assertThat(resolution(openAfter)).isEqualTo("REMOVED");
        assertThat(resolvedBy(openAfter)).isEqualTo(admin.getId());
    }

    @Test
    void memberDeletionStaysTheMembersWhenAdminRemovesItAgain() throws Exception {
        UUID comment = comment(alice, "Is there parking?");
        perform(delete("/api/me/comments/{id}", comment), memberToken(alice)).andExpect(status().isNoContent());
        UUID open = report("COMMENT", comment, bob, null);

        remove(comment, "Already gone").andExpect(status().isNoContent());

        assertThat(deletedBy(comment)).isEqualTo(alice.getId());
        assertThat(deleteReason(comment)).isNull();
        assertThat(resolution(open)).isEqualTo("REMOVED");
        assertThat(resolvedBy(open)).isEqualTo(admin.getId());
    }

    @Test
    void unknownCommentAnswersNotFound() throws Exception {
        remove(UUID.randomUUID(), "Spam").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Comment not found."));

        assertThat(commentCount()).isZero();
    }

    @Test
    void blankOrTooLongReasonAnswersBadRequest() throws Exception {
        UUID comment = comment(alice, "Is there parking?");

        remove(comment, "   ").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.reason").value("Please give a reason"));
        remove(comment, "a".repeat(1001)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.reason").value("Reason must not exceed 1000 characters"));
        remove(comment, "a".repeat(1000)).andExpect(status().isNoContent());

        assertThat(deleteReason(comment)).hasSize(1000);
    }

    @Test
    void officialReplyIsMarkedOfficial() throws Exception {
        UUID comment = comment(alice, "Is there parking?");

        postOfficialReply(comment, "  Yes, behind the venue  ")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.body").value("Yes, behind the venue"))
                .andExpect(jsonPath("$.official").value(true))
                .andExpect(jsonPath("$.author.displayName").value("Ada"))
                .andExpect(jsonPath("$.mine").value(true))
                .andExpect(jsonPath("$.replies").isEmpty());

        mockMvc.perform(get("/api/events/{id}/comments", SHOW_ID))
                .andExpect(jsonPath("$.items[0].official").value(false))
                .andExpect(jsonPath("$.items[0].replies[0].official").value(true))
                .andExpect(jsonPath("$.items[0].replies[0].body").value("Yes, behind the venue"))
                .andExpect(jsonPath("$.items[0].replies[0].author.displayName").value("Ada"));
        perform(get("/api/events/{id}/comments", SHOW_ID), adminToken())
                .andExpect(jsonPath("$.items[0].replies[0].mine").value(true));
    }

    @Test
    void officialReplyToAReplyAnswersBadRequest() throws Exception {
        UUID comment = comment(alice, "Is there parking?");
        UUID reply = reply(bob, comment, "Yes");
        UUID official = createdOfficialReply(comment, "The lot is open");

        postOfficialReply(reply, "Thanks").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("A reply can't be replied to."));
        postOfficialReply(official, "And again").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("A reply can't be replied to."));

        assertThat(commentCount()).isEqualTo(3);
    }

    @Test
    void officialReplyToADeletedOrUnknownCommentAnswersNotFound() throws Exception {
        UUID comment = comment(alice, "Is there parking?");
        perform(delete("/api/me/comments/{id}", comment), memberToken(alice)).andExpect(status().isNoContent());

        postOfficialReply(comment, "Yes").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Comment not found."));
        postOfficialReply(UUID.randomUUID(), "Yes").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Comment not found."));

        assertThat(commentCount()).isEqualTo(1);
    }

    @Test
    void officialReplyOnAHiddenEventAnswersNotFound() throws Exception {
        UUID comment = comment(alice, "Is there parking?");
        jdbc.sql("UPDATE publication.event_snapshot SET discoverable = false, state = 'UNAVAILABLE'").update();

        postOfficialReply(comment, "Yes").andExpect(status().isNotFound());

        assertThat(commentCount()).isEqualTo(1);
    }

    @Test
    void emptyOrTooLongOfficialReplyAnswersBadRequest() throws Exception {
        UUID comment = comment(alice, "Is there parking?");

        postOfficialReply(comment, "   ").andExpect(status().isBadRequest());
        postOfficialReply(comment, "a".repeat(2001)).andExpect(status().isBadRequest());

        assertThat(commentCount()).isEqualTo(1);
    }

    @Test
    void adminCanRemoveAnOfficialReply() throws Exception {
        UUID comment = comment(alice, "Is there parking?");
        UUID official = createdOfficialReply(comment, "Secret staff note");

        remove(official, "Posted on the wrong event").andExpect(status().isNoContent());

        assertThat(deletedBy(official)).isEqualTo(admin.getId());
        assertThat(deleteReason(official)).isEqualTo("Posted on the wrong event");
        mockMvc.perform(get("/api/events/{id}/comments", SHOW_ID))
                .andExpect(content().string(not(containsString("Secret staff note"))))
                .andExpect(jsonPath("$.items[0].body").value("Is there parking?"))
                .andExpect(jsonPath("$.items[0].replies").isEmpty());
    }

    @Test
    void membersCannotRemoveOtherMembersCommentsOrPostOfficialReplies() throws Exception {
        UUID comment = comment(alice, "Is there parking?");

        perform(delete("/api/me/comments/{id}", comment), memberToken(bob)).andExpect(status().isNotFound());
        perform(post("/api/admin/comments/{id}/official-replies", comment), memberToken(alice), "body", "Official?")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Forbidden"));
        removeAs(comment, "Spam", memberToken(alice)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Forbidden"));

        mockMvc.perform(get("/api/events/{id}/comments", SHOW_ID))
                .andExpect(jsonPath("$.items[0].body").value("Is there parking?"))
                .andExpect(jsonPath("$.items[0].deleted").value(false))
                .andExpect(jsonPath("$.items[0].replies").isEmpty());
        assertThat(commentCount()).isEqualTo(1);
    }

    @Test
    void signedOutCallsAnswerUnauthorized() throws Exception {
        UUID comment = comment(alice, "Is there parking?");

        mockMvc.perform(json(delete("/api/admin/comments/{id}", comment), "reason", "Spam"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(json(post("/api/admin/comments/{id}/official-replies", comment), "body", "Yes"))
                .andExpect(status().isUnauthorized());

        assertThat(commentCount()).isEqualTo(1);
        assertThat(deleteReason(comment)).isNull();
    }

    private UUID comment(UserAccount user, String body) throws Exception {
        return id(perform(post("/api/events/{id}/comments", SHOW_ID), memberToken(user), "body", body)
                .andExpect(status().isCreated()));
    }

    private UUID reply(UserAccount user, UUID commentId, String body) throws Exception {
        return id(perform(post("/api/comments/{id}/replies", commentId), memberToken(user), "body", body)
                .andExpect(status().isCreated()));
    }

    private ResultActions postOfficialReply(UUID commentId, String body) throws Exception {
        return perform(post("/api/admin/comments/{id}/official-replies", commentId), adminToken(), "body", body);
    }

    private UUID createdOfficialReply(UUID commentId, String body) throws Exception {
        return id(postOfficialReply(commentId, body).andExpect(status().isCreated()));
    }

    private ResultActions remove(UUID commentId, String reason) throws Exception {
        return removeAs(commentId, reason, adminToken());
    }

    private ResultActions removeAs(UUID commentId, String reason, String token) throws Exception {
        return perform(delete("/api/admin/comments/{id}", commentId), token, "reason", reason);
    }

    private UUID report(String targetType, UUID targetId, UserAccount reporter, String resolution) {
        UUID id = UUID.randomUUID();
        if (resolution == null) {
            jdbc.sql("""
                    INSERT INTO app.report (id, target_type, target_id, reporter_id, reason)
                    VALUES (:id, :targetType, :targetId, :reporterId, 'SPAM')
                    """)
                    .param("id", id)
                    .param("targetType", targetType)
                    .param("targetId", targetId)
                    .param("reporterId", reporter.getId())
                    .update();
            return id;
        }
        jdbc.sql("""
                INSERT INTO app.report (
                    id, target_type, target_id, reporter_id, reason, resolved_at, resolved_by, resolution
                ) VALUES (
                    :id, :targetType, :targetId, :reporterId, 'SPAM', TIMESTAMP '2030-01-01 00:00:00+00',
                    :reporterId, :resolution
                )
                """)
                .param("id", id)
                .param("targetType", targetType)
                .param("targetId", targetId)
                .param("reporterId", reporter.getId())
                .param("resolution", resolution)
                .update();
        return id;
    }

    private UUID commentIdOfOnlyComment() {
        return jdbc.sql("SELECT id FROM app.comment").query(UUID.class).single();
    }

    private UUID deletedBy(UUID commentId) {
        return jdbc.sql("SELECT deleted_by FROM app.comment WHERE id = :id")
                .param("id", commentId).query(UUID.class).single();
    }

    private String deleteReason(UUID commentId) {
        return jdbc.sql("SELECT delete_reason FROM app.comment WHERE id = :id")
                .param("id", commentId).query(String.class).optional().orElse(null);
    }

    private Instant deletedAt(UUID commentId) {
        return jdbc.sql("SELECT deleted_at FROM app.comment WHERE id = :id")
                .param("id", commentId).query(OffsetDateTime.class).single().toInstant();
    }

    private String resolution(UUID reportId) {
        return jdbc.sql("SELECT resolution FROM app.report WHERE id = :id")
                .param("id", reportId).query(String.class).optional().orElse(null);
    }

    private UUID resolvedBy(UUID reportId) {
        return jdbc.sql("SELECT resolved_by FROM app.report WHERE id = :id")
                .param("id", reportId).query(UUID.class).single();
    }

    private boolean isResolved(UUID reportId) {
        return jdbc.sql("SELECT resolved_at IS NOT NULL FROM app.report WHERE id = :id")
                .param("id", reportId).query(Boolean.class).single();
    }

    private long commentCount() {
        return jdbc.sql("SELECT count(*) FROM app.comment").query(Long.class).single();
    }

    private String adminToken() {
        return token(admin.getId().toString(), List.of("ADMIN"));
    }

    private String memberToken(UserAccount user) {
        return accessTokenService.issueAccessToken(user);
    }

    private String token(String subject, List<String> roles) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(subject)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plus(Duration.ofMinutes(5)))
                .claim("roles", roles)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, String token, String field, String value)
            throws Exception {
        return perform(json(request, field, value), token);
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, String token) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String field,
                                                       String value) {
        return request.contentType(MediaType.APPLICATION_JSON)
                .content("{\"" + field + "\": \"" + value + "\"}");
    }

    private static UUID id(ResultActions result) throws Exception {
        return UUID.fromString(JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id"));
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
}
