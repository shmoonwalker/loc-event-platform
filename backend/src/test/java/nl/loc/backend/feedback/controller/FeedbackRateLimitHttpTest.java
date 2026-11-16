package nl.loc.backend.feedback.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(properties = {
        "app.rate-limit.policies.feedback.capacity=2",
        "app.rate-limit.policies.feedback.period=1h"})
@AutoConfigureMockMvc
@Testcontainers
class FeedbackRateLimitHttpTest {

    private static final String IP = "203.0.113.10";
    private static final String OTHER_IP = "203.0.113.20";

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

    private static final GenericContainer<?> redis = new GenericContainer<>("redis:8.2-alpine").withExposedPorts(6379);

    static {
        // start before the context reads the mapped port
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
    void feedbackIsLimitedPerIp() throws Exception {
        UserAccount member = userRepository.save(new UserAccount("member@example.com", "Member", "hash", Instant.now()));
        String memberToken = accessTokenService.issueAccessToken(member);

        send(IP, null).andExpect(status().isCreated());
        send(IP, null).andExpect(status().isCreated());
        send(IP, null)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
        send(IP, memberToken).andExpect(status().isTooManyRequests());
        send(OTHER_IP, null).andExpect(status().isCreated());

        assertThat(jdbc.sql("SELECT count(*) FROM app.feedback").query(Long.class).single()).isEqualTo(3);
    }

    private ResultActions send(String remoteAddr, String token) throws Exception {
        MockHttpServletRequestBuilder request = post("/api/feedback")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\": \"The map is hard to use\"}")
                .with(httpRequest -> {
                    httpRequest.setRemoteAddr(remoteAddr);
                    return httpRequest;
                });
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return mockMvc.perform(request);
    }
}
