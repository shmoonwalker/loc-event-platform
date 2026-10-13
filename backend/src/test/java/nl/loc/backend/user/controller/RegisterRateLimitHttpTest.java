package nl.loc.backend.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import nl.loc.backend.user.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(properties = {
        "app.rate-limit.policies.register.capacity=2",
        "app.rate-limit.policies.register.period=1h"})
@AutoConfigureMockMvc
@Testcontainers
class RegisterRateLimitHttpTest {
    @Container
    @ServiceConnection
    private static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

    @Container
    private static final GenericContainer<?> redis = new GenericContainer<>("redis:8.2-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void redisUrl(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.url", () -> "redis://" + redis.getHost() + ":" + redis.getMappedPort(6379));
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserAccountRepository repository;

    @Test
    void blocksTheSameIpAfterTheLimitWithRetryAfter() throws Exception {
        register("203.0.113.1", "first@example.com").andExpect(status().isAccepted());
        register("203.0.113.1", "second@example.com").andExpect(status().isAccepted());

        register("203.0.113.1", "third@example.com")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.title").value("Too many requests"));

        assertThat(repository.existsByEmail("third@example.com")).isFalse();
        // another IP has its own bucket
        register("203.0.113.2", "fourth@example.com").andExpect(status().isAccepted());
    }

    private ResultActions register(String ip, String email) throws Exception {
        return mockMvc.perform(post("/api/auth/register")
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "correct-horse-battery", "displayName": "Alice"}
                        """.formatted(email)));
    }
}
