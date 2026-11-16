package nl.loc.backend.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import nl.loc.backend.user.mail.AccountMailer;
import nl.loc.backend.user.model.PasswordResetToken;
import nl.loc.backend.user.model.UserAccount;
import nl.loc.backend.user.repository.PasswordResetTokenRepository;
import nl.loc.backend.user.repository.UserAccountRepository;
import nl.loc.backend.user.service.RefreshTokenService;
import nl.loc.backend.user.service.SecureTokens;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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
class PasswordResetHttpTest {
    @Container
    @ServiceConnection
    private static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

    private static final String NEW_PASSWORD = "brand-new-password";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserAccountRepository userRepository;
    @Autowired
    private PasswordResetTokenRepository tokenRepository;
    @Autowired
    private RefreshTokenService refreshTokens;
    @Autowired
    private PasswordEncoder passwords;
    @MockitoBean
    private AccountMailer mailer;

    private UserAccount user;

    @BeforeEach
    void createVerifiedUser() {
        userRepository.deleteAll();
        UserAccount account = new UserAccount("alice@example.com", "Alice", passwords.encode("old-password-1"), Instant.now());
        account.markEmailVerified(Instant.now());
        user = userRepository.save(account);
    }

    @ParameterizedTest
    @ValueSource(strings = {"alice@example.com", "nobody@example.com"})
    void knownAndUnknownEmailsGetTheSameEmptyAcceptedResponse(String email) throws Exception {
        forgot(email).andExpect(status().isAccepted()).andExpect(content().string(""));
    }

    @Test
    void unknownEmailGetsNoMailAndNoToken() throws Exception {
        forgot("nobody@example.com").andExpect(status().isAccepted());

        verifyNoInteractions(mailer);
        assertThat(tokenRepository.count()).isZero();
    }

    @Test
    void knownEmailInAnySpellingGetsALinkAndOnlyTheHashIsStored() throws Exception {
        forgot("Alice@Example.COM").andExpect(status().isAccepted());

        String token = sentToken();
        assertThat(tokenRepository.findAll()).singleElement().satisfies(stored -> {
            assertThat(stored.getTokenHash()).isEqualTo(SecureTokens.hash(token));
            assertThat(Duration.between(stored.getCreatedAt(), stored.getExpiresAt())).isEqualTo(Duration.ofMinutes(30));
        });
    }

    @Test
    void validTokenSetsTheNewPasswordOnce() throws Exception {
        forgot("alice@example.com").andExpect(status().isAccepted());
        String token = sentToken();

        reset(token, NEW_PASSWORD).andExpect(status().isNoContent()).andExpect(content().string(""));
        login(NEW_PASSWORD).andExpect(status().isOk());
        login("old-password-1").andExpect(status().isUnauthorized());

        reset(token, "another-password")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid token"));
        login(NEW_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void expiredTokenIsRejectedAndThePasswordStays() throws Exception {
        String token = SecureTokens.generate();
        tokenRepository.save(new PasswordResetToken(user, SecureTokens.hash(token),
                Instant.now().minus(Duration.ofMinutes(31)), Duration.ofMinutes(30)));

        reset(token, NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid token"));
        login("old-password-1").andExpect(status().isOk());
    }

    @Test
    void unknownTokenIsRejected() throws Exception {
        reset("not-a-real-token", NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("This link is invalid or has expired"));
    }

    @Test
    void aNewerRequestInvalidatesTheEarlierLink() throws Exception {
        String first = SecureTokens.generate();
        tokenRepository.save(new PasswordResetToken(user, SecureTokens.hash(first), Instant.now(), Duration.ofMinutes(30)));

        forgot("alice@example.com").andExpect(status().isAccepted());

        reset(first, NEW_PASSWORD).andExpect(status().isBadRequest());
        reset(sentToken(), NEW_PASSWORD).andExpect(status().isNoContent());
    }

    @Test
    void resetSignsOutEverySession() throws Exception {
        String session = refreshTokens.issueToken(user);
        forgot("alice@example.com").andExpect(status().isAccepted());

        reset(sentToken(), NEW_PASSWORD).andExpect(status().isNoContent());

        mockMvc.perform(post("/api/auth/refresh").cookie(new Cookie(RefreshTokenCookie.NAME, session)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid refresh token"));
    }

    @Test
    void resetVerifiesAnUnverifiedAccount() throws Exception {
        UserAccount unverified = userRepository.save(
                new UserAccount("una@example.com", "Una", passwords.encode("old-password-1"), Instant.now()));
        forgot("una@example.com").andExpect(status().isAccepted());
        ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
        verify(mailer).sendPasswordResetLink(eq(unverified.getId()), eq("una@example.com"), token.capture());

        reset(token.getValue(), NEW_PASSWORD).andExpect(status().isNoContent());

        assertThat(userRepository.findById(unverified.getId()).orElseThrow().isEmailVerified()).isTrue();
    }

    @Test
    void newPasswordFollowsTheRegistrationRules() throws Exception {
        forgot("alice@example.com").andExpect(status().isAccepted());
        String token = sentToken();

        reset(token, "too-short")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.newPassword").value("Password must be at least 10 characters long"));
        reset(token, "€".repeat(25)).andExpect(status().isBadRequest());

        // a refused password leaves the token usable
        reset(token, NEW_PASSWORD).andExpect(status().isNoContent());
    }

    @Test
    void invalidEmailReturnsValidationProblem() throws Exception {
        forgot("not-an-email")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").value("Please provide a valid email address"));
    }

    private String sentToken() {
        ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
        verify(mailer).sendPasswordResetLink(eq(user.getId()), eq("alice@example.com"), token.capture());
        return token.getValue();
    }

    private ResultActions forgot(String email) throws Exception {
        return send("/api/auth/forgot-password", Map.of("email", email));
    }

    private ResultActions reset(String token, String newPassword) throws Exception {
        return send("/api/auth/reset-password", Map.of("token", token, "newPassword", newPassword));
    }

    private ResultActions login(String password) throws Exception {
        return send("/api/auth/login", Map.of("email", "alice@example.com", "password", password));
    }

    private ResultActions send(String path, Map<String, String> body) throws Exception {
        return mockMvc.perform(post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(JsonMapper.shared().writeValueAsString(body)));
    }
}
