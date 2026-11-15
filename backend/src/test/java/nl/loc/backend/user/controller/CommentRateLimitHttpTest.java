package nl.loc.backend.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.util.UUID;
import nl.loc.backend.user.model.UserAccount;
import nl.loc.backend.user.repository.UserAccountRepository;
import nl.loc.backend.user.service.AccessTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(properties = {
        "app.rate-limit.policies.comment.capacity=2",
        "app.rate-limit.policies.comment.period=1h"})
@AutoConfigureMockMvc
@Testcontainers
@Sql(scripts = "/db/publication-discoverable-events.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class CommentRateLimitHttpTest {

    private static final UUID SHOW_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

    private static final GenericContainer<?> redis = new GenericContainer<>("redis:8.2-alpine").withExposedPorts(6379);

    static {
        // @Sql before the class loads the context before @Container would start Redis, so start it here
        redis.start();
    }

    @DynamicPropertySource
    static void redisUrl(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.url", () -> "redis://" + redis.getHost() + ":" + redis.getMappedPort(6379));
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
    void commentsAndRepliesShareOneLimitPerMemberAndEditsAreNotLimited() throws Exception {
        jdbc.sql("""
                INSERT INTO publication.event_snapshot (
                    loc_occurrence_id, loc_event_id, loc_group_id, revision, discoverable, state, payload,
                    first_published_at, updated_at
                ) VALUES (gen_random_uuid(), :id, :id, 1, true, 'UPCOMING', '{}'::jsonb, now(), now())
                """).param("id", SHOW_ID).update();
        String alice = token(userRepository.save(new UserAccount("alice@example.com", "Alice", "hash", Instant.now())));
        String bob = token(userRepository.save(new UserAccount("bob@example.com", "Bob", "hash", Instant.now())));

        String comment = JsonPath.read(perform(post("/api/events/{id}/comments", SHOW_ID), alice)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        perform(post("/api/comments/{id}/replies", comment), alice).andExpect(status().isCreated());

        perform(post("/api/events/{id}/comments", SHOW_ID), alice)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
        perform(patch("/api/me/comments/{id}", comment), alice).andExpect(status().isOk());
        perform(post("/api/events/{id}/comments", SHOW_ID), bob).andExpect(status().isCreated());

        assertThat(jdbc.sql("SELECT count(*) FROM app.comment").query(Long.class).single()).isEqualTo(3);
    }

    private String token(UserAccount user) {
        return accessTokenService.issueAccessToken(user);
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, String token) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\": \"Hello\"}"));
    }
}
