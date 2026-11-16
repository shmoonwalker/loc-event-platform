package nl.loc.backend.feedback.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
class FeedbackHttpTest {

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

    private UserAccount member;
    private UserAccount admin;

    @BeforeEach
    void setUp() {
        jdbc.sql("DELETE FROM app.feedback").update();
        userRepository.deleteAll();
        member = userRepository.save(new UserAccount("member@example.com", "Member", "hash", Instant.now()));
        admin = userRepository.save(new UserAccount("admin@example.com", "Admin", "hash", Instant.now()));
    }

    @Test
    void anonymousVisitorCanSendFeedback() throws Exception {
        UUID id = id(send("{\"message\": \"  The map is hard to use  \", \"contactEmail\": \"   \"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("The map is hard to use"))
                .andExpect(jsonPath("$.userId").doesNotExist())
                .andExpect(jsonPath("$.contactEmail").doesNotExist()));

        Stored stored = stored(id);
        assertThat(stored.userId()).isNull();
        assertThat(stored.message()).isEqualTo("The map is hard to use");
        assertThat(stored.contactEmail()).isNull();
        assertThat(stored.handledAt()).isNull();
    }

    @Test
    void memberFeedbackStoresTheTokenUserNotABodyUserId() throws Exception {
        String body = """
                {"message": "  Hello  ", "contactEmail": "  Member@Example.com  ", "userId": "%s"}
                """.formatted(UUID.randomUUID());

        UUID id = id(send(body, token(member))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(member.getId().toString()))
                .andExpect(jsonPath("$.message").value("Hello"))
                .andExpect(jsonPath("$.contactEmail").value("Member@Example.com")));

        assertThat(stored(id).userId()).isEqualTo(member.getId());
        assertThat(stored(id).contactEmail()).isEqualTo("Member@Example.com");
    }

    @Test
    void emptyOrTooLongMessageOrInvalidEmailAnswersBadRequest() throws Exception {
        send("{\"message\": \"   \"}").andExpect(status().isBadRequest());
        send("{\"message\": \"" + "a".repeat(2001) + "\"}").andExpect(status().isBadRequest());
        send("{\"message\": \"Hi\", \"contactEmail\": \"not-an-email\"}").andExpect(status().isBadRequest());
        send("{\"message\": \"Hi\", \"contactEmail\": \"" + "a".repeat(250) + "@x.co\"}")
                .andExpect(status().isBadRequest());

        send("{\"message\": \"" + "a".repeat(2000) + "\"}").andExpect(status().isCreated());
        assertThat(count()).isEqualTo(1);
    }

    @Test
    void badTokenAnswersUnauthorizedAndStoresNothing() throws Exception {
        mockMvc.perform(post("/api/feedback")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\": \"Hi\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));

        assertThat(count()).isZero();
    }

    @Test
    void adminListsOpenFeedbackFirstThenNewest() throws Exception {
        UUID olderOpen = id(send("{\"message\": \"Older open\"}"));
        UUID newerOpen = id(send("{\"message\": \"Newer open\"}"));
        UUID handledNewest = id(send("{\"message\": \"Handled but newest\"}"));
        createdAt(olderOpen, Instant.parse("2026-01-01T00:00:00Z"));
        createdAt(newerOpen, Instant.parse("2026-01-03T00:00:00Z"));
        createdAt(handledNewest, Instant.parse("2026-01-04T00:00:00Z"));
        jdbc.sql("UPDATE app.feedback SET handled_at = :handled, handled_by = :admin WHERE id = :id")
                .param("handled", OffsetDateTime.parse("2026-01-05T00:00:00Z"))
                .param("admin", admin.getId())
                .param("id", handledNewest)
                .update();

        perform(get("/api/admin/feedback").param("page", "0").param("size", "2"), adminToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].id").value(newerOpen.toString()))
                .andExpect(jsonPath("$.items[1].id").value(olderOpen.toString()));
        perform(get("/api/admin/feedback").param("page", "1").param("size", "2"), adminToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(handledNewest.toString()))
                .andExpect(jsonPath("$.items[0].handledBy").value(admin.getId().toString()));
    }

    @Test
    void adminMarksFeedbackHandledAndMarkingAgainChangesNothing() throws Exception {
        UUID id = id(send("{\"message\": \"Please add dark mode\"}"));
        UserAccount otherAdmin = userRepository.save(new UserAccount("other@example.com", "Other", "hash", Instant.now()));

        String handledAt = JsonPath.read(perform(post("/api/admin/feedback/{id}/handled", id), adminToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.handledBy").value(admin.getId().toString()))
                .andReturn().getResponse().getContentAsString(), "$.handledAt");

        perform(post("/api/admin/feedback/{id}/handled", id), adminToken(otherAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.handledAt").value(handledAt))
                .andExpect(jsonPath("$.handledBy").value(admin.getId().toString()));

        assertThat(stored(id).handledBy()).isEqualTo(admin.getId());
    }

    @Test
    void unknownFeedbackAnswersNotFound() throws Exception {
        perform(post("/api/admin/feedback/{id}/handled", UUID.randomUUID()), adminToken())
                .andExpect(status().isNotFound());
        perform(post("/api/admin/feedback/{id}/handled", "not-a-uuid"), adminToken())
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidPageOrSizeAnswersBadRequest() throws Exception {
        perform(get("/api/admin/feedback").param("page", "-1"), adminToken()).andExpect(status().isBadRequest());
        perform(get("/api/admin/feedback").param("size", "0"), adminToken()).andExpect(status().isBadRequest());
        perform(get("/api/admin/feedback").param("size", "21"), adminToken()).andExpect(status().isBadRequest());
    }

    @Test
    void membersAndVisitorsCannotListOrHandleFeedback() throws Exception {
        UUID id = id(send("{\"message\": \"Hi\"}"));

        mockMvc.perform(get("/api/admin/feedback")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/admin/feedback/{id}/handled", id)).andExpect(status().isUnauthorized());
        perform(get("/api/admin/feedback"), token(member))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
        perform(post("/api/admin/feedback/{id}/handled", id), token(member)).andExpect(status().isForbidden());

        assertThat(stored(id).handledAt()).isNull();
    }

    private String token(UserAccount user) {
        return accessTokenService.issueAccessToken(user);
    }

    private String adminToken() {
        return adminToken(admin);
    }

    private String adminToken(UserAccount user) {
        Instant expiresAt = Instant.now().plus(Duration.ofMinutes(5));
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(user.getId().toString())
                .issuedAt(expiresAt.minus(Duration.ofMinutes(15)))
                .expiresAt(expiresAt)
                .claim("roles", List.of("ADMIN"))
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    private ResultActions send(String json) throws Exception {
        return mockMvc.perform(post("/api/feedback").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions send(String json, String token) throws Exception {
        return mockMvc.perform(post("/api/feedback")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, String token) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private static UUID id(ResultActions result) throws Exception {
        return UUID.fromString(JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id"));
    }

    private long count() {
        return jdbc.sql("SELECT count(*) FROM app.feedback").query(Long.class).single();
    }

    private void createdAt(UUID id, Instant created) {
        jdbc.sql("UPDATE app.feedback SET created_at = :created WHERE id = :id")
                .param("created", created.atOffset(ZoneOffset.UTC))
                .param("id", id)
                .update();
    }

    private Stored stored(UUID id) {
        return jdbc.sql("""
                SELECT user_id, message, contact_email, handled_at, handled_by
                FROM app.feedback WHERE id = :id
                """)
                .param("id", id)
                .query((rs, row) -> readStored(rs))
                .single();
    }

    private static Stored readStored(ResultSet rs) throws SQLException {
        OffsetDateTime handledAt = rs.getObject("handled_at", OffsetDateTime.class);
        return new Stored(
                rs.getObject("user_id", UUID.class),
                rs.getString("message"),
                rs.getString("contact_email"),
                handledAt == null ? null : handledAt.toInstant(),
                rs.getObject("handled_by", UUID.class));
    }

    private record Stored(UUID userId, String message, String contactEmail, Instant handledAt, UUID handledBy) {
    }
}
