package nl.loc.backend.user.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import org.springframework.security.crypto.password.PasswordEncoder;
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
        "app.rate-limit.policies.account-security.capacity=2",
        "app.rate-limit.policies.account-security.period=1h"})
@AutoConfigureMockMvc
@Testcontainers
class AccountSecurityRateLimitHttpTest {

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
    private UserAccountRepository userRepository;
    @Autowired
    private AccessTokenService accessTokenService;
    @Autowired
    private PasswordEncoder passwords;

    @Test
    void accountChangesShareTheMemberLimitIncludingWrongPasswordAttempts() throws Exception {
        String token = memberToken("alice@example.com");
        String passwordBody = "{\"currentPassword\":\"incorrect\",\"newPassword\":\"Another-strong-password1!\"}";
        String emailBody = "{\"currentPassword\":\"incorrect\",\"newEmail\":\"new@example.com\"}";
        String deleteBody = "{\"currentPassword\":\"incorrect\"}";

        perform(post("/api/me/password"), token, passwordBody).andExpect(status().isUnauthorized());
        perform(post("/api/me/email"), token, emailBody).andExpect(status().isUnauthorized());
        perform(delete("/api/me"), token, deleteBody)
                .andExpect(status().isTooManyRequests()).andExpect(header().exists(HttpHeaders.RETRY_AFTER));
        perform(delete("/api/me"), memberToken("bob@example.com"), deleteBody).andExpect(status().isUnauthorized());
    }

    private String memberToken(String email) {
        UserAccount user = new UserAccount(email, "Member", passwords.encode("correct-password"), Instant.now());
        return accessTokenService.issueAccessToken(userRepository.save(user));
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, String token, String body) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
