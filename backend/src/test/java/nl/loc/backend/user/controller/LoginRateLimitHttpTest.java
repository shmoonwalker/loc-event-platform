package nl.loc.backend.user.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

// real HTTP through embedded Tomcat, so the forwarded client IP is resolved like in production
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "app.rate-limit.policies.login.capacity=2",
                "app.rate-limit.policies.login.period=1h",
                "app.rate-limit.policies.login-email.capacity=3",
                "app.rate-limit.policies.login-email.period=1h"})
@Testcontainers
class LoginRateLimitHttpTest {
    @Container
    @ServiceConnection
    private static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

    @Container
    private static final GenericContainer<?> redis = new GenericContainer<>("redis:8.2-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void redisUrl(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.url", () -> "redis://" + redis.getHost() + ":" + redis.getMappedPort(6379));
    }

    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @Test
    void blocksIpAndEmailAfterTheLimitWithRetryAfter() throws Exception {
        assertThat(login("203.0.113.1", "first@example.com").statusCode()).isEqualTo(401);
        // a different spelling of the same email shares the bucket
        assertThat(login("203.0.113.1", " FIRST@Example.com").statusCode()).isEqualTo(401);

        HttpResponse<String> blocked = login("203.0.113.1", "first@example.com");
        assertThat(blocked.statusCode()).isEqualTo(429);
        assertThat(blocked.headers().firstValue("Retry-After")).hasValueSatisfying(
                seconds -> assertThat(Long.parseLong(seconds)).isPositive());
        assertThat(blocked.body()).contains("\"title\":\"Too many requests\"");

        // another email from the same IP has its own bucket
        assertThat(login("203.0.113.1", "other@example.com").statusCode()).isEqualTo(401);
    }

    @Test
    void blocksAnEmailGuessedFromManyIps() throws Exception {
        assertThat(login("198.51.100.1", "target@example.com").statusCode()).isEqualTo(401);
        assertThat(login("198.51.100.2", "target@example.com").statusCode()).isEqualTo(401);
        assertThat(login("198.51.100.3", "target@example.com").statusCode()).isEqualTo(401);

        assertThat(login("198.51.100.4", "target@example.com").statusCode()).isEqualTo(429);
    }

    private HttpResponse<String> login(String forwardedFor, String email) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/auth/login"))
                .header("Content-Type", "application/json")
                .header("X-Forwarded-For", forwardedFor)
                .POST(HttpRequest.BodyPublishers.ofString("""
                        {"email": "%s", "password": "wrong-password"}
                        """.formatted(email)))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
