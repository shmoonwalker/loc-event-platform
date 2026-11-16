package nl.loc.backend.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import nl.loc.backend.user.model.UserAccount;
import nl.loc.backend.user.repository.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = "spring.data.redis.url=redis://localhost:1")
class AccountDeletionHttpTest {

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserAccountRepository accounts;
    @Autowired
    private PasswordEncoder encoder;
    @Autowired
    private JdbcClient jdbc;

    private UserAccount user;
    private String accessToken;
    private Cookie refreshCookie;

    @BeforeEach
    void signIn() throws Exception {
        accounts.deleteAll();
        user = new UserAccount("member@example.com", "Member", encoder.encode("correct password"), Instant.now());
        user.markEmailVerified(Instant.now());
        user = accounts.save(user);
        MockHttpServletResponse response = login().andExpect(status().isOk()).andReturn().getResponse();
        accessToken = JsonPath.read(response.getContentAsString(), "$.accessToken");
        refreshCookie = response.getCookie(RefreshTokenCookie.NAME);
        jdbc.sql("""
                INSERT INTO app.email_verification_token(user_id, token_hash, expires_at)
                VALUES (:userId, :tokenHash, now() + interval '1 day')
                """).param("userId", user.getId()).param("tokenHash", new byte[32]).update();
        jdbc.sql("INSERT INTO app.saved_event(user_id, event_id) VALUES (:userId, uuidv7())")
                .param("userId", user.getId()).update();
        jdbc.sql("INSERT INTO app.followed_organizer(user_id, organizer_id) VALUES (:userId, uuidv7())")
                .param("userId", user.getId()).update();
    }

    @Test
    void deleteAccountRemovesPersonalDataAndInvalidatesLoginAndRefresh() throws Exception {
        deleteAccount("correct password").andExpect(status().isNoContent())
                .andExpect(result -> assertThat(result.getResponse().getCookie(RefreshTokenCookie.NAME).getMaxAge()).isZero());

        assertThat(accounts.findById(user.getId())).isEmpty();
        assertThat(rowCount("saved_event")).isZero();
        assertThat(rowCount("followed_organizer")).isZero();
        assertThat(rowCount("refresh_token")).isZero();
        assertThat(rowCount("email_verification_token")).isZero();
        login().andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/refresh").cookie(refreshCookie)).andExpect(status().isBadRequest());
    }

    @Test
    void wrongPasswordKeepsPersonalDataAndSession() throws Exception {
        deleteAccount("wrong password").andExpect(status().isUnauthorized());

        assertThat(accounts.findById(user.getId())).isPresent();
        assertThat(rowCount("email_verification_token")).isOne();
        assertThat(rowCount("saved_event")).isOne();
        assertThat(rowCount("followed_organizer")).isOne();
        mockMvc.perform(post("/api/auth/refresh").cookie(refreshCookie)).andExpect(status().isOk());
    }

    private long rowCount(String table) {
        return jdbc.sql("SELECT count(*) FROM app." + table + " WHERE user_id = :userId")
                .param("userId", user.getId()).query(Long.class).single();
    }

    private ResultActions login() throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"member@example.com\",\"password\":\"correct password\"}"));
    }

    private ResultActions deleteAccount(String password) throws Exception {
        return mockMvc.perform(delete("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"currentPassword\":\"" + password + "\"}"));
    }
}
