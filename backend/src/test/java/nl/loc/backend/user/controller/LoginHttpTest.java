package nl.loc.backend.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Map;
import nl.loc.backend.user.model.UserAccount;
import nl.loc.backend.user.repository.EmailVerificationTokenRepository;
import nl.loc.backend.user.repository.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
// nothing listens on port 1, so the rate limiter fails open and never limits these tests
@TestPropertySource(properties = "spring.data.redis.url=redis://localhost:1")
class LoginHttpTest {
    private static final String PASSWORD = "correct-horse-battery";

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
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JwtDecoder jwtDecoder;

    private UserAccount verifiedUser;

    @BeforeEach
    void createUsers() {
        tokenRepository.deleteAll();
        userRepository.deleteAll();
        verifiedUser = new UserAccount("alice@example.com", "Alice", passwordEncoder.encode(PASSWORD), Instant.now());
        verifiedUser.markEmailVerified(Instant.now());
        verifiedUser = userRepository.save(verifiedUser);
        userRepository.save(new UserAccount("bob@example.com", "Bob", passwordEncoder.encode(PASSWORD), Instant.now()));
    }

    @Test
    void verifiedUserGetsAccessTokenWithIdAndRole() throws Exception {
        String body = login("alice@example.com", PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andReturn().getResponse().getContentAsString();

        JsonNode response = JsonMapper.shared().readTree(body);
        Jwt token = jwtDecoder.decode(response.get("accessToken").asString());
        assertThat(token.getSubject()).isEqualTo(verifiedUser.getId().toString());
        assertThat(token.getClaimAsStringList("roles")).containsExactly("USER");
    }

    @Test
    void emailIsMatchedCaseInsensitively() throws Exception {
        login(" ALICE@Example.com ", PASSWORD).andExpect(status().isOk());
    }

    @Test
    void wrongPasswordAndUnknownEmailGetTheSameAnswer() throws Exception {
        String wrongPassword = login("alice@example.com", "wrong-password")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        String unknownEmail = login("nobody@example.com", PASSWORD)
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(unknownEmail).isEqualTo(wrongPassword);
        assertThat(wrongPassword).contains("Invalid email or password");
    }

    @Test
    void unverifiedUserWithCorrectPasswordIsForbidden() throws Exception {
        login("bob@example.com", PASSWORD)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Email not verified"))
                .andExpect(jsonPath("$.accessToken").doesNotExist());
    }

    @Test
    void unverifiedUserWithWrongPasswordDoesNotLearnTheAccountIsUnverified() throws Exception {
        login("bob@example.com", "wrong-password")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Invalid credentials"));
    }

    @Test
    void passwordLongerThanBcryptLimitIsRejectedNotAnError() throws Exception {
        login("alice@example.com", "x".repeat(100)).andExpect(status().isUnauthorized());
    }

    @Test
    void blankFieldsReturnValidationProblem() throws Exception {
        login(" ", " ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").value("Please provide an email"))
                .andExpect(jsonPath("$.errors.password").value("Please provide a password"));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(JsonMapper.shared().writeValueAsString(Map.of("email", email, "password", password))));
    }
}
