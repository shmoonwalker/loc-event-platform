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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
class ResendVerificationHttpTest {
    @Container
    @ServiceConnection
    private static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserAccountRepository userRepository;
    @Autowired
    private EmailVerificationTokenRepository tokenRepository;

    private UserAccount unverified;

    @BeforeEach
    void createAccounts() {
        tokenRepository.deleteAll();
        userRepository.deleteAll();
        unverified = userRepository.save(new UserAccount("unverified@example.com", "Una", "hash", Instant.now()));
        UserAccount verified = new UserAccount("verified@example.com", "Vera", "hash", Instant.now());
        verified.markEmailVerified(Instant.now());
        userRepository.save(verified);
    }

    @ParameterizedTest
    @ValueSource(strings = {"unverified@example.com", "verified@example.com", "nobody@example.com"})
    void everyEmailGetsTheSameEmptyAcceptedResponse(String email) throws Exception {
        resend(Map.of("email", email)).andExpect(status().isAccepted()).andExpect(content().string(""));
    }

    @Test
    void onlyTheUnverifiedAccountGetsANewToken() throws Exception {
        resend(Map.of("email", "Unverified@Example.COM")).andExpect(status().isAccepted());
        resend(Map.of("email", "verified@example.com")).andExpect(status().isAccepted());
        resend(Map.of("email", "nobody@example.com")).andExpect(status().isAccepted());

        // the user is lazy, but reading a proxy's id needs no session
        assertThat(tokenRepository.findAll()).singleElement()
                .satisfies(token -> assertThat(token.getUser().getId()).isEqualTo(unverified.getId()));
    }

    @Test
    void invalidEmailReturnsValidationProblem() throws Exception {
        resend(Map.of("email", "not-an-email"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.email").value("Please provide a valid email address"));
    }

    private ResultActions resend(Map<String, String> request) throws Exception {
        return mockMvc.perform(post("/api/auth/resend-verification").contentType(MediaType.APPLICATION_JSON)
                .content(JsonMapper.shared().writeValueAsString(request)));
    }
}
