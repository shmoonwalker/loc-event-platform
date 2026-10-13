package nl.loc.backend.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;
import nl.loc.backend.user.model.Role;
import nl.loc.backend.user.model.UserAccount;
import nl.loc.backend.user.repository.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class RegisterHttpTest {
    @Container
    @ServiceConnection
    private static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserAccountRepository repository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void clearUsers() {
        repository.deleteAll();
    }

    @Test
    void anonymousRegistrationReturnsAcceptedAndPersistsAccount() throws Exception {
        register(validRequest()).andExpect(status().isAccepted()).andExpect(content().string(""));

        assertThat(repository.findAll()).singleElement().satisfies(account -> {
            assertThat(account.getId()).isNotNull();
            assertThat(account.getEmail()).isEqualTo("alice@example.com");
            assertThat(account.getDisplayName()).isEqualTo("Alice");
            assertThat(account.getRole()).isEqualTo(Role.USER);
            assertThat(account.getCreatedAt()).isNotNull();
            assertThat(account.getPasswordHash()).isNotEqualTo("correct-horse-battery");
            assertThat(passwordEncoder.matches("correct-horse-battery", account.getPasswordHash())).isTrue();
        });
    }

    @Test
    void duplicateEmailReturnsAcceptedWithoutChangingAccount() throws Exception {
        register(validRequest()).andExpect(status().isAccepted()).andExpect(content().string(""));
        UserAccount original = repository.findAll().getFirst();
        Map<String, String> duplicate = Map.of(
                "email", "alice@example.com", "password", "different-password", "displayName", "Someone else");

        register(duplicate).andExpect(status().isAccepted()).andExpect(content().string(""));

        assertThat(repository.findAll()).singleElement().satisfies(account -> {
            assertThat(account.getId()).isEqualTo(original.getId());
            assertThat(account.getPasswordHash()).isEqualTo(original.getPasswordHash());
            assertThat(account.getDisplayName()).isEqualTo(original.getDisplayName());
            assertThat(account.getCreatedAt()).isEqualTo(original.getCreatedAt());
        });
    }

    @ParameterizedTest
    @MethodSource("invalidFields")
    void invalidInputReturnsValidationProblemWithoutCreatingAccount(String field, String value, String errorField)
            throws Exception {
        Map<String, String> request = validRequest();
        request.put(field, value);

        register(request).andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors." + errorField).isNotEmpty());
        assertThat(repository.count()).isZero();
    }

    static Stream<Arguments> invalidFields() {
        Stream<Arguments> required = Stream.of("email", "password", "displayName")
                .flatMap(field -> Stream.of(null, "", "   ").map(value -> Arguments.of(field, value, field)));
        return Stream.concat(required, Stream.of(
                Arguments.of("email", "not-an-email", "email"),
                Arguments.of("email", "a".repeat(243) + "@example.com", "email"),
                Arguments.of("password", "123456789", "password"),
                Arguments.of("password", "a".repeat(73), "passwordWithinByteLimit"),
                Arguments.of("password", "é".repeat(37), "passwordWithinByteLimit"),
                Arguments.of("displayName", "a".repeat(101), "displayName")));
    }

    @ParameterizedTest
    @MethodSource("boundaryPasswords")
    void acceptsPasswordsAtLengthBoundaries(String password) throws Exception {
        Map<String, String> request = validRequest();
        request.put("password", password);

        register(request).andExpect(status().isAccepted()).andExpect(content().string(""));
        assertThat(repository.findAll()).singleElement().satisfies(account ->
                assertThat(passwordEncoder.matches(password, account.getPasswordHash())).isTrue());
    }

    static Stream<String> boundaryPasswords() {
        return Stream.of("1234567890", "a".repeat(72), "é".repeat(36));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "{", "null"})
    void missingOrMalformedBodyReturnsProblemWithoutCreatingAccount(String body) throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Malformed request body"));
        assertThat(repository.count()).isZero();
    }

    private static Map<String, String> validRequest() {
        return new LinkedHashMap<>(Map.of(
                "email", "Alice@Example.COM", "password", "correct-horse-battery", "displayName", "  Alice  "));
    }

    private ResultActions register(Map<String, String> request) throws Exception {
        return mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(JsonMapper.shared().writeValueAsString(request)));
    }
}
