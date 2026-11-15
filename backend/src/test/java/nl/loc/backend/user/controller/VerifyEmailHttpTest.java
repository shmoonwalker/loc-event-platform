package nl.loc.backend.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Map;
import nl.loc.backend.user.mail.AccountMailer;
import nl.loc.backend.user.model.UserAccount;
import nl.loc.backend.user.service.AccessTokenService;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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

    @Autowired
    private PasswordEncoder passwords;
    @Autowired
    private AccessTokenService accessTokens;
    @MockitoBean
    private AccountMailer mailer;

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

    @Test
    void newAddressIsVerifiedBeforeTheChangeAndTheOldAddressIsNotified() throws Exception {
        prepareVerifiedAccount();
        requestChange("NEW@example.com", "current-password")
                .andExpect(status().isAccepted()).andExpect(content().string(""));
        assertThat(userRepository.existsByEmail("alice@example.com")).isTrue();
        assertThat(userRepository.existsByEmail("new@example.com")).isFalse();
        String token = changeToken("new@example.com");

        verify(Map.of("token", token)).andExpect(status().isNoContent());
        assertThat(userRepository.existsByEmail("alice@example.com")).isFalse();
        assertThat(userRepository.findByEmail("new@example.com").orElseThrow().getId()).isEqualTo(user.getId());
        org.mockito.Mockito.verify(mailer).sendEmailChanged(user.getId(), "alice@example.com");
        verify(Map.of("token", token)).andExpect(status().isBadRequest());
    }

    @Test
    void duplicateAddressGetsTheSameAcceptedResponseAndWrongPasswordChangesNothing() throws Exception {
        prepareVerifiedAccount();
        userRepository.save(new UserAccount("taken@example.com", "Other", "hash", Instant.now()));
        requestChange("taken@example.com", "current-password")
                .andExpect(status().isAccepted()).andExpect(content().string(""));
        requestChange("new@example.com", "wrong").andExpect(status().isUnauthorized());
        assertThat(tokenRepository.count()).isZero();
        assertThat(userRepository.existsByEmail("alice@example.com")).isTrue();
        verifyNoInteractions(mailer);
    }

    @Test
    void aLaterRequestInvalidatesThePreviousLinkAndAnotherAccountCanClaimTheNewAddress() throws Exception {
        prepareVerifiedAccount();
        requestChange("first@example.com", "current-password").andExpect(status().isAccepted());
        String firstToken = changeToken("first@example.com");
        requestChange("second@example.com", "current-password").andExpect(status().isAccepted());
        String secondToken = changeToken("second@example.com");
        verify(Map.of("token", firstToken)).andExpect(status().isBadRequest());
        userRepository.save(new UserAccount("second@example.com", "Other", "hash", Instant.now()));
        verify(Map.of("token", secondToken)).andExpect(status().isBadRequest());
        assertThat(userRepository.existsByEmail("alice@example.com")).isTrue();
    }

    private void prepareVerifiedAccount() {
        user.replaceRegistration(passwords.encode("current-password"), "Alice");
        user.markEmailVerified(Instant.now());
        user = userRepository.save(user);
    }

    private String changeToken(String address) {
        ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(mailer).sendEmailChangeLink(eq(user.getId()), eq(address), token.capture());
        return token.getValue();
    }

    private ResultActions requestChange(String newEmail, String currentPassword) throws Exception {
        return mockMvc.perform(post("/api/me/email")
                .header("Authorization", "Bearer " + accessTokens.issueAccessToken(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(JsonMapper.shared().writeValueAsString(Map.of("newEmail", newEmail, "currentPassword", currentPassword))));
    }

    private ResultActions verify(Map<String, String> request) throws Exception {
        return mockMvc.perform(post("/api/auth/verify-email").contentType(MediaType.APPLICATION_JSON)
                .content(JsonMapper.shared().writeValueAsString(request)));
    }
}
