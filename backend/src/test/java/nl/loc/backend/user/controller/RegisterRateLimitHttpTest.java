package nl.loc.backend.user.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import nl.loc.backend.user.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

// real HTTP through embedded Tomcat, so forward-headers-strategy resolves the client IP like in production
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "app.rate-limit.policies.register.capacity=2",
                "app.rate-limit.policies.register.period=1h"})
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

    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;
    @Autowired
    private UserAccountRepository repository;

    @Test
    void blocksTheForwardedClientIpAfterTheLimitWithRetryAfter() throws Exception {
        assertThat(register("203.0.113.1", "first@example.com").statusCode()).isEqualTo(202);
        assertThat(register("203.0.113.1", "second@example.com").statusCode()).isEqualTo(202);

        HttpResponse<String> blocked = register("203.0.113.1", "third@example.com");
        assertThat(blocked.statusCode()).isEqualTo(429);
        assertThat(blocked.headers().firstValue("Retry-After")).hasValueSatisfying(
                seconds -> assertThat(Long.parseLong(seconds)).isPositive());
        assertThat(blocked.body()).contains("\"title\":\"Too many requests\"");
        assertThat(repository.findByEmail("third@example.com")).isEmpty();

        // the test client connects from 127.0.0.1 every time, so a fresh bucket proves the forwarded IP is the key
        assertThat(register("203.0.113.2", "fourth@example.com").statusCode()).isEqualTo(202);
    }

    @Test
    void ignoresClientSuppliedForwardedForEntries() throws Exception {
        // a proxy appends the real client (198.51.100.1) after whatever the client sent itself
        assertThat(register("1.1.1.1, 198.51.100.1", "spoof1@example.com").statusCode()).isEqualTo(202);
        assertThat(register("2.2.2.2, 198.51.100.1", "spoof2@example.com").statusCode()).isEqualTo(202);

        assertThat(register("3.3.3.3, 198.51.100.1", "spoof3@example.com").statusCode()).isEqualTo(429);
    }

    private HttpResponse<String> register(String forwardedFor, String email) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/auth/register"))
                .header("Content-Type", "application/json")
                .header("X-Forwarded-For", forwardedFor)
                .POST(HttpRequest.BodyPublishers.ofString("""
                        {"email": "%s", "password": "correct-horse-battery", "displayName": "Alice"}
                        """.formatted(email)))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
