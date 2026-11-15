package nl.loc.backend.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
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
class CommentHttpTest {

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
        jdbc.sql("DELETE FROM app.comment").update();
        userRepository.deleteAll();
        jdbc.sql("DELETE FROM publication.event_snapshot").update();
        alice = userRepository.save(new UserAccount("alice@example.com", "Alice", "hash", Instant.now()));
        bob = userRepository.save(new UserAccount("bob@example.com", "Bob", "hash", Instant.now()));
        publish(SHOW_ID, SHOW_ID, true, "UPCOMING");
    }

    @Test
    void anyoneCanReadCommentsWithTheirReplies() throws Exception {
        UUID comment = comment(alice, SHOW_ID, "Is there parking?");
        reply(bob, comment, "Yes, behind the venue");

        mockMvc.perform(get("/api/events/{id}/comments", SHOW_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(comment.toString()))
                .andExpect(jsonPath("$.items[0].author.displayName").value("Alice"))
                .andExpect(jsonPath("$.items[0].body").value("Is there parking?"))
                .andExpect(jsonPath("$.items[0].mine").value(false))
                .andExpect(jsonPath("$.items[0].deleted").value(false))
                .andExpect(jsonPath("$.items[0].replies[0].author.displayName").value("Bob"))
                .andExpect(jsonPath("$.items[0].replies[0].body").value("Yes, behind the venue"));
    }

    @Test
    void commentsAreNewestFirstAndRepliesOldestFirst() throws Exception {
        UUID older = comment(alice, SHOW_ID, "First");
        UUID newer = comment(bob, SHOW_ID, "Second");
        reply(bob, older, "Reply one");
        reply(alice, older, "Reply two");

        mockMvc.perform(get("/api/events/{id}/comments", SHOW_ID))
                .andExpect(jsonPath("$.items[0].id").value(newer.toString()))
                .andExpect(jsonPath("$.items[0].replies").isEmpty())
                .andExpect(jsonPath("$.items[1].id").value(older.toString()))
                .andExpect(jsonPath("$.items[1].replies[0].body").value("Reply one"))
                .andExpect(jsonPath("$.items[1].replies[1].body").value("Reply two"));
    }

    @Test
    void pagesTopLevelComments() throws Exception {
        comment(alice, SHOW_ID, "First");
        comment(alice, SHOW_ID, "Second");
        comment(alice, SHOW_ID, "Third");

        mockMvc.perform(get("/api/events/{id}/comments", SHOW_ID).param("page", "1").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].body").value("First"));
    }

    @Test
    void mineMarksTheViewersOwnComments() throws Exception {
        comment(alice, SHOW_ID, "Mine");

        perform(get("/api/events/{id}/comments", SHOW_ID), token(alice))
                .andExpect(jsonPath("$.items[0].mine").value(true));
        perform(get("/api/events/{id}/comments", SHOW_ID), token(bob))
                .andExpect(jsonPath("$.items[0].mine").value(false));
    }

    @Test
    void postingAnswersCreatedWithTheStoredComment() throws Exception {
        perform(post("/api/events/{id}/comments", SHOW_ID), token(alice), "  Is there parking?  ")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.body").value("Is there parking?"))
                .andExpect(jsonPath("$.author.displayName").value("Alice"))
                .andExpect(jsonPath("$.mine").value(true))
                .andExpect(jsonPath("$.editedAt").doesNotExist());
    }

    @Test
    void commentsBelongToTheShowAlsoWhenPostedThroughAnOldEventId() throws Exception {
        UUID show = UUID.randomUUID();
        UUID oldEventId = UUID.randomUUID();
        publish(show, oldEventId, true, "UPCOMING");

        comment(alice, oldEventId, "Posted through an old link");

        assertThat(jdbc.sql("SELECT event_id FROM app.comment").query(UUID.class).single()).isEqualTo(show);
        mockMvc.perform(get("/api/events/{id}/comments", show))
                .andExpect(jsonPath("$.items[0].body").value("Posted through an old link"));
    }

    @Test
    void signedOutWritesAnswerUnauthorized() throws Exception {
        UUID comment = comment(alice, SHOW_ID, "Is there parking?");

        mockMvc.perform(withBody(post("/api/events/{id}/comments", SHOW_ID), "Hi")).andExpect(status().isUnauthorized());
        mockMvc.perform(withBody(post("/api/comments/{id}/replies", comment), "Hi")).andExpect(status().isUnauthorized());
        mockMvc.perform(withBody(patch("/api/me/comments/{id}", comment), "Hi")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/me/comments/{id}", comment)).andExpect(status().isUnauthorized());

        assertThat(commentCount()).isEqualTo(1);
    }

    @Test
    void emptyOrTooLongTextAnswersBadRequest() throws Exception {
        perform(post("/api/events/{id}/comments", SHOW_ID), token(alice), "   ").andExpect(status().isBadRequest());
        perform(post("/api/events/{id}/comments", SHOW_ID), token(alice), "a".repeat(2001))
                .andExpect(status().isBadRequest());

        assertThat(commentCount()).isZero();
    }

    @Test
    void postingAfterTheAccountWasDeletedAnswersUnauthorized() throws Exception {
        String token = token(alice);
        userRepository.delete(alice);

        perform(post("/api/events/{id}/comments", SHOW_ID), token, "Hi").andExpect(status().isUnauthorized());
    }

    @Test
    void unknownEventAnswersNotFound() throws Exception {
        UUID unknown = UUID.randomUUID();

        mockMvc.perform(get("/api/events/{id}/comments", unknown)).andExpect(status().isNotFound());
        perform(post("/api/events/{id}/comments", unknown), token(alice), "Hi").andExpect(status().isNotFound());
    }

    @Test
    void hiddenEventShowsAndTakesNoComments() throws Exception {
        UUID hidden = UUID.randomUUID();
        publish(hidden, hidden, false, "UNAVAILABLE");

        mockMvc.perform(get("/api/events/{id}/comments", hidden)).andExpect(status().isNotFound());
        perform(post("/api/events/{id}/comments", hidden), token(alice), "Hi").andExpect(status().isNotFound());

        assertThat(commentCount()).isZero();
    }

    @Test
    void commentsOnAHiddenEventAreNotShownAndCannotBeRepliedTo() throws Exception {
        UUID comment = comment(alice, SHOW_ID, "Is there parking?");
        jdbc.sql("UPDATE publication.event_snapshot SET discoverable = false, state = 'UNAVAILABLE'").update();

        mockMvc.perform(get("/api/events/{id}/comments", SHOW_ID)).andExpect(status().isNotFound());
        perform(post("/api/comments/{id}/replies", comment), token(bob), "Yes").andExpect(status().isNotFound());
    }

    @Test
    void inactiveEventStaysOpenForComments() throws Exception {
        UUID cancelled = UUID.randomUUID();
        publish(cancelled, cancelled, false, "CANCELLED");

        perform(post("/api/events/{id}/comments", cancelled), token(alice), "Will we get a refund?")
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/events/{id}/comments", cancelled))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].body").value("Will we get a refund?"));
    }

    @Test
    void replyToAReplyAnswersBadRequest() throws Exception {
        UUID comment = comment(alice, SHOW_ID, "Is there parking?");
        UUID reply = reply(bob, comment, "Yes");

        perform(post("/api/comments/{id}/replies", reply), token(alice), "Thanks").andExpect(status().isBadRequest());

        assertThat(commentCount()).isEqualTo(2);
    }

    @Test
    void replyToADeletedOrUnknownCommentAnswersNotFound() throws Exception {
        UUID comment = comment(alice, SHOW_ID, "Is there parking?");
        perform(delete("/api/me/comments/{id}", comment), token(alice)).andExpect(status().isNoContent());

        perform(post("/api/comments/{id}/replies", comment), token(bob), "Yes").andExpect(status().isNotFound());
        perform(post("/api/comments/{id}/replies", UUID.randomUUID()), token(bob), "Yes")
                .andExpect(status().isNotFound());
    }

    @Test
    void editingOwnCommentShowsItAsEdited() throws Exception {
        UUID comment = comment(alice, SHOW_ID, "Is there parking?");

        perform(patch("/api/me/comments/{id}", comment), token(alice), "Is there bike parking?")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body").value("Is there bike parking?"))
                .andExpect(jsonPath("$.editedAt").isNotEmpty());
        mockMvc.perform(get("/api/events/{id}/comments", SHOW_ID))
                .andExpect(jsonPath("$.items[0].body").value("Is there bike parking?"))
                .andExpect(jsonPath("$.items[0].editedAt").isNotEmpty());
    }

    @Test
    void someoneElsesCommentAnswersNotFoundAndStaysUnchanged() throws Exception {
        UUID comment = comment(alice, SHOW_ID, "Is there parking?");

        perform(patch("/api/me/comments/{id}", comment), token(bob), "Hacked").andExpect(status().isNotFound());
        perform(delete("/api/me/comments/{id}", comment), token(bob)).andExpect(status().isNotFound());

        mockMvc.perform(get("/api/events/{id}/comments", SHOW_ID))
                .andExpect(jsonPath("$.items[0].body").value("Is there parking?"))
                .andExpect(jsonPath("$.items[0].deleted").value(false));
    }

    @Test
    void editingADeletedCommentAnswersNotFound() throws Exception {
        UUID comment = comment(alice, SHOW_ID, "Is there parking?");
        perform(delete("/api/me/comments/{id}", comment), token(alice)).andExpect(status().isNoContent());

        perform(patch("/api/me/comments/{id}", comment), token(alice), "Back again").andExpect(status().isNotFound());
    }

    @Test
    void deletingTwiceAnswersNoContent() throws Exception {
        UUID comment = comment(alice, SHOW_ID, "Is there parking?");

        perform(delete("/api/me/comments/{id}", comment), token(alice)).andExpect(status().isNoContent());
        perform(delete("/api/me/comments/{id}", comment), token(alice)).andExpect(status().isNoContent());
    }

    @Test
    void deletedCommentWithRepliesShowsWithoutTextSoRepliesKeepTheirPlace() throws Exception {
        UUID comment = comment(alice, SHOW_ID, "Secret text");
        reply(bob, comment, "A reply");

        perform(delete("/api/me/comments/{id}", comment), token(alice)).andExpect(status().isNoContent());

        perform(get("/api/events/{id}/comments", SHOW_ID), token(alice))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Secret text"))))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].deleted").value(true))
                .andExpect(jsonPath("$.items[0].body").doesNotExist())
                .andExpect(jsonPath("$.items[0].author").doesNotExist())
                .andExpect(jsonPath("$.items[0].mine").value(false))
                .andExpect(jsonPath("$.items[0].replies[0].body").value("A reply"));
        // the row stays for moderation
        assertThat(commentCount()).isEqualTo(2);
    }

    @Test
    void deletedCommentWithoutRepliesIsLeftOut() throws Exception {
        UUID comment = comment(alice, SHOW_ID, "Never mind");
        UUID withDeletedReply = comment(bob, SHOW_ID, "Question");
        UUID reply = reply(alice, withDeletedReply, "Oops");

        perform(delete("/api/me/comments/{id}", comment), token(alice)).andExpect(status().isNoContent());
        perform(delete("/api/me/comments/{id}", reply), token(alice)).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/events/{id}/comments", SHOW_ID))
                .andExpect(content().string(not(containsString("Never mind"))))
                .andExpect(content().string(not(containsString("Oops"))))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(withDeletedReply.toString()))
                .andExpect(jsonPath("$.items[0].replies").isEmpty());
    }

    @Test
    void deletedAccountKeepsItsCommentsWithoutAuthor() throws Exception {
        comment(alice, SHOW_ID, "Is there parking?");

        userRepository.delete(alice);

        mockMvc.perform(get("/api/events/{id}/comments", SHOW_ID))
                .andExpect(jsonPath("$.items[0].body").value("Is there parking?"))
                .andExpect(jsonPath("$.items[0].author").doesNotExist())
                .andExpect(jsonPath("$.items[0].deleted").value(false));
    }

    private UUID comment(UserAccount user, UUID eventId, String body) throws Exception {
        return id(perform(post("/api/events/{id}/comments", eventId), token(user), body)
                .andExpect(status().isCreated()));
    }

    private UUID reply(UserAccount user, UUID commentId, String body) throws Exception {
        return id(perform(post("/api/comments/{id}/replies", commentId), token(user), body)
                .andExpect(status().isCreated()));
    }

    private static UUID id(ResultActions result) throws Exception {
        return UUID.fromString(JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id"));
    }

    private String token(UserAccount user) {
        return accessTokenService.issueAccessToken(user);
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, String token) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, String token, String body) throws Exception {
        return perform(withBody(request, body), token);
    }

    private static MockHttpServletRequestBuilder withBody(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content("{\"body\": \"" + body + "\"}");
    }

    private long commentCount() {
        return jdbc.sql("SELECT count(*) FROM app.comment").query(Long.class).single();
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
