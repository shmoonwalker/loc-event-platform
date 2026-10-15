package nl.loc.backend.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Map;
import nl.loc.backend.user.model.UserAccount;
import nl.loc.backend.user.repository.EmailVerificationTokenRepository;
import nl.loc.backend.user.repository.UserAccountRepository;
import nl.loc.backend.user.service.EmailVerificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
// nothing listens on port 1, so the rate limiter fails open and never limits these tests
@TestPropertySource(properties = "spring.data.redis.url=redis://localhost:1")
class VerifyEmailHttpTest {
    @Container
    @ServiceConnection
    private static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserAccountRepository userRepository;
    @Autowired
    private EmailVerificationTokenRepository tokenRepository;
    @Autowired
    private EmailVerificationService emailVerificationService;

    private UserAccount user;

    @BeforeEach
    void createUnverifiedUser() {
        tokenRepository.deleteAll();
        userRepository.deleteAll();
        user = userRepository.save(new UserAccount("alice@example.com", "Alice", "hash", Instant.now()));
    }

    @Test
    void validTokenVerifiesEmailAndReturnsNoContent() throws Exception {
        String token = emailVerificationService.issueToken(user);

        verify(Map.of("token", token)).andExpect(status().isNoContent()).andExpect(content().string(""));

        assertThat(userRepository.findById(user.getId()).orElseThrow().isEmailVerified()).isTrue();
    }

    @Test
    void usedTokenIsRejected() throws Exception {
        String token = emailVerificationService.issueToken(user);
        verify(Map.of("token", token)).andExpect(status().isNoContent());

        verify(Map.of("token", token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid token"));
    }

    @Test
    void unknownTokenIsRejectedWithoutVerifying() throws Exception {
        verify(Map.of("token", "not-a-real-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid token"))
                .andExpect(jsonPath("$.detail").value("This link is invalid or has expired"));

        assertThat(userRepository.findById(user.getId()).orElseThrow().isEmailVerified()).isFalse();
    }

    @Test
    void blankTokenReturnsValidationProblem() throws Exception {
        verify(Map.of("token", " "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.token").value("Please provide a token"));
    }

    private ResultActions verify(Map<String, String> request) throws Exception {
        return mockMvc.perform(post("/api/auth/verify-email").contentType(MediaType.APPLICATION_JSON)
                .content(JsonMapper.shared().writeValueAsString(request)));
    }
}
