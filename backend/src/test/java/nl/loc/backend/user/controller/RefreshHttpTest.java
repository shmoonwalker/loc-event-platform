package nl.loc.backend.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.Map;
import nl.loc.backend.user.model.UserAccount;
import nl.loc.backend.user.repository.RefreshTokenRepository;
import nl.loc.backend.user.repository.UserAccountRepository;
import nl.loc.backend.user.service.RefreshTokenService;
import nl.loc.backend.user.service.SecureTokens;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
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
class RefreshHttpTest {
    private static final String PASSWORD = "correct-horse-battery";

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserAccountRepository userRepository;
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private RefreshTokenService refreshTokenService;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void createUser() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
        UserAccount user = new UserAccount("alice@example.com", "Alice", passwordEncoder.encode(PASSWORD), Instant.now());
        user.markEmailVerified(Instant.now());
        userRepository.save(user);
    }

    @Test
    void loginSetsRefreshCookieAndStoresOnlyItsHash() throws Exception {
        Cookie cookie = login();

        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getSecure()).isTrue();
        assertThat(cookie.getPath()).isEqualTo("/api/auth");
        assertThat(refreshTokenRepository.findAll()).singleElement()
                .satisfies(token -> assertThat(token.getTokenHash()).isEqualTo(SecureTokens.hash(cookie.getValue())));
    }

    @Test
    void refreshReturnsNewAccessTokenAndRotatesTheCookie() throws Exception {
        Cookie first = login();

        MvcResult result = refresh(first)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();

        Cookie second = result.getResponse().getCookie(RefreshTokenCookie.NAME);
        assertThat(second.getValue()).isNotBlank().isNotEqualTo(first.getValue());
        assertThat(second.getMaxAge()).isPositive().isLessThanOrEqualTo(first.getMaxAge());
    }

    @Test
    void reusingARotatedTokenRevokesTheWholeFamily() throws Exception {
        Cookie first = login();
        Cookie second = refresh(first).andExpect(status().isOk())
                .andReturn().getResponse().getCookie(RefreshTokenCookie.NAME);
        moveRotationOutOfTheGraceWindow();

        refresh(first)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid refresh token"))
                .andExpect(result -> assertThat(
                        result.getResponse().getCookie(RefreshTokenCookie.NAME).getMaxAge()).isZero());

        // the revoke must survive the failed request, or the stolen successor keeps working
        refresh(second).andExpect(status().isBadRequest());
    }

    @Test
    void parallelRefreshFromAnotherTabGetsConflictAndKeepsTheSession() throws Exception {
        Cookie first = login();
        Cookie second = refresh(first).andExpect(status().isOk())
                .andReturn().getResponse().getCookie(RefreshTokenCookie.NAME);

        refresh(first)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Session already refreshed"))
                .andExpect(result -> assertThat(result.getResponse().getCookie(RefreshTokenCookie.NAME)).isNull());

        refresh(second).andExpect(status().isOk());
    }

    @Test
    void cleanupDeletesOnlyExpiredTokens() throws Exception {
        login();
        jdbc.update("update app.refresh_token set expires_at = now() - interval '1 minute'");
        Cookie live = login();

        refreshTokenService.deleteExpired();

        assertThat(refreshTokenRepository.findAll()).singleElement()
                .satisfies(token -> assertThat(token.getTokenHash()).isEqualTo(SecureTokens.hash(live.getValue())));
    }

    @Test
    void logoutRevokesTheSession() throws Exception {
        Cookie cookie = login();

        mockMvc.perform(post("/api/auth/logout").cookie(cookie))
                .andExpect(status().isNoContent())
                .andExpect(result -> assertThat(
                        result.getResponse().getCookie(RefreshTokenCookie.NAME).getMaxAge()).isZero());

        refresh(cookie).andExpect(status().isBadRequest());
    }

    @Test
    void refreshWithoutCookieIsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/refresh")).andExpect(status().isBadRequest());
    }

    private void moveRotationOutOfTheGraceWindow() {
        jdbc.update("update app.refresh_token set created_at = created_at - interval '1 minute'");
    }

    private Cookie login() throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(JsonMapper.shared().writeValueAsString(
                                Map.of("email", "alice@example.com", "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie(RefreshTokenCookie.NAME);
    }

    private ResultActions refresh(Cookie cookie) throws Exception {
        return mockMvc.perform(post("/api/auth/refresh").cookie(cookie));
    }
}
