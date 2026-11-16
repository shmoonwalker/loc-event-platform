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
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ForgotPasswordRateLimitHttpTest {
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
    void blocksIpAndEmailAfterFiveRequestsWithRetryAfter() throws Exception {
        for (int i = 0; i < 4; i++) {
            assertThat(forgot("203.0.113.1", "first@example.com").statusCode()).isEqualTo(202);
        }
        // a different spelling of the same email shares the bucket
        assertThat(forgot("203.0.113.1", "FIRST@Example.com").statusCode()).isEqualTo(202);

        HttpResponse<String> blocked = forgot("203.0.113.1", "first@example.com");
        assertThat(blocked.statusCode()).isEqualTo(429);
        assertThat(blocked.headers().firstValue("Retry-After")).hasValueSatisfying(
                seconds -> assertThat(Long.parseLong(seconds)).isPositive());
        assertThat(blocked.body()).contains("\"title\":\"Too many requests\"");

        // another email from the same IP, and the same email from another IP, have their own buckets
        assertThat(forgot("203.0.113.1", "other@example.com").statusCode()).isEqualTo(202);
        assertThat(forgot("203.0.113.2", "first@example.com").statusCode()).isEqualTo(202);
    }

    private HttpResponse<String> forgot(String forwardedFor, String email) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/auth/forgot-password"))
                .header("Content-Type", "application/json")
                .header("X-Forwarded-For", forwardedFor)
                .POST(HttpRequest.BodyPublishers.ofString("""
                        {"email": "%s"}
                        """.formatted(email)))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
