package nl.loc.backend.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import javax.crypto.spec.SecretKeySpec;
import nl.loc.backend.user.model.UserAccount;
import nl.loc.backend.user.repository.EmailVerificationTokenRepository;
import nl.loc.backend.user.repository.UserAccountRepository;
import nl.loc.backend.user.service.AccessTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
// nothing listens on port 1, so the rate limiter fails open and never limits these tests
@TestPropertySource(properties = "spring.data.redis.url=redis://localhost:1")
class AuthorizationHttpTest {
    private static final String OTHER_SECRET = "8m8qJx6Ow3tKkK3PZ0a4cQ2u7L1vYb9sN5eR0dT6fHg=";

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
    private AccessTokenService accessTokenService;
    @Autowired
    private JwtEncoder jwtEncoder;

    private UserAccount user;

    @BeforeEach
    void createUser() {
        tokenRepository.deleteAll();
        userRepository.deleteAll();
        user = new UserAccount("alice@example.com", "Alice", "{bcrypt}$2a$12$hash", Instant.now());
        user.markEmailVerified(Instant.now());
        user = userRepository.save(user);
    }

    @Test
    void meReturnsCurrentUserWithoutPasswordMaterial() throws Exception {
        perform(get("/api/auth/me"), accessTokenService.issueAccessToken(user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId().toString()))
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.displayName").value("Alice"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(content().string(not(containsString("bcrypt"))));
    }

    @Test
    void meWithoutTokenIsUnauthorizedProblem() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void malformedExpiredAndForeignTokensGetTheSameAnswerAsNoToken() throws Exception {
        String noToken = mockMvc.perform(get("/api/auth/me")).andReturn().getResponse().getContentAsString();
        // the decoder allows 60 seconds of clock skew, so expire well before that
        String expired = token(user.getId().toString(), List.of("USER"), Instant.now().minus(Duration.ofMinutes(5)));
        NimbusJwtEncoder otherKeyEncoder = new NimbusJwtEncoder(new ImmutableSecret<>(
                new SecretKeySpec(Base64.getDecoder().decode(OTHER_SECRET), "HmacSHA256")));
        String foreign = otherKeyEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims(user.getId().toString(), List.of("ADMIN"),
                        Instant.now().plus(Duration.ofMinutes(5))))).getTokenValue();

        for (String token : List.of("not-a-jwt", expired, foreign)) {
            String body = perform(get("/api/auth/me"), token)
                    .andExpect(status().isUnauthorized())
                    .andReturn().getResponse().getContentAsString();
            assertThat(body).isEqualTo(noToken);
        }
    }

    @Test
    void tokenOfDeletedAccountIsUnauthorized() throws Exception {
        String token = accessTokenService.issueAccessToken(user);
        userRepository.deleteAll();

        perform(get("/api/auth/me"), token)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Unauthorized"));
    }

    @Test
    void invalidTokenOnPublicRouteIsUnauthorized() throws Exception {
        perform(get("/api/categories"), "not-a-jwt").andExpect(status().isUnauthorized());
    }

    @Test
    void adminRoutesRejectAnonymousCallers() throws Exception {
        mockMvc.perform(get("/api/admin/anything")).andExpect(status().isUnauthorized());
    }

    @Test
    void adminRoutesForbidUsers() throws Exception {
        perform(get("/api/admin/anything"), accessTokenService.issueAccessToken(user))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Forbidden"));
    }

    @Test
    void adminRoutesLetAdminsThrough() throws Exception {
        String adminToken = token(user.getId().toString(), List.of("ADMIN"), Instant.now().plus(Duration.ofMinutes(5)));

        // no admin endpoints exist yet: 404 means security let the request through
        perform(get("/api/admin/anything"), adminToken).andExpect(status().isNotFound());
    }

    @Test
    void routesThatAreNotOpenedAreDeniedEvenWithValidToken() throws Exception {
        perform(get("/api/not-opened"), accessTokenService.issueAccessToken(user)).andExpect(status().isForbidden());
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, String token) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private String token(String subject, List<String> roles, Instant expiresAt) {
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims(subject, roles, expiresAt))).getTokenValue();
    }

    private static JwtClaimsSet claims(String subject, List<String> roles, Instant expiresAt) {
        return JwtClaimsSet.builder()
                .subject(subject)
                .issuedAt(expiresAt.minus(Duration.ofMinutes(15)))
                .expiresAt(expiresAt)
                .claim("roles", roles)
                .build();
    }
}
