package nl.loc.backend.photo.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.imageio.ImageIO;
import nl.loc.backend.photo.JpegFixtures;
import nl.loc.backend.photo.service.EventPhotoStorage;
import nl.loc.backend.photo.service.EventPhotoStorageUnavailableException;
import nl.loc.backend.user.model.UserAccount;
import nl.loc.backend.user.repository.UserAccountRepository;
import nl.loc.backend.user.service.AccessTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.NestedTestConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
// nothing listens on port 1, so the rate limiter fails open and never limits these tests
@TestPropertySource(properties = {
        "spring.data.redis.url=redis://localhost:1",
        "app.event-photo.storage.public-url=https://photos.test"
})
@Sql(scripts = "/db/publication-discoverable-events.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class EventPhotoHttpTest {

    private static final UUID SHOW_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

    @MockitoBean
    private EventPhotoStorage storage;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserAccountRepository userRepository;
    @Autowired
    private AccessTokenService accessTokenService;
    @Autowired
    private JwtEncoder jwtEncoder;
    @Autowired
    private JdbcClient jdbc;

    private final RecordingStorage stored = new RecordingStorage();
    private UserAccount alice;
    private UserAccount bob;
    private UserAccount admin;

    @BeforeEach
    void setUp() {
        stored.clear();
        stored.bind(storage);
        jdbc.sql("DELETE FROM app.report").update();
        jdbc.sql("DELETE FROM app.event_photo").update();
        userRepository.deleteAll();
        jdbc.sql("DELETE FROM publication.event_snapshot").update();
        alice = userRepository.save(new UserAccount("alice@example.com", "Alice", "hash", Instant.now()));
        bob = userRepository.save(new UserAccount("bob@example.com", "Bob", "hash", Instant.now()));
        admin = userRepository.save(new UserAccount("ada@example.com", "Ada", "hash", Instant.now()));
        publish(SHOW_ID, SHOW_ID, true, "UPCOMING");
    }

    @Test
    void aPendingPhotoIsHiddenAndHasNoPublicUrlUntilAnAdminApprovesIt() throws Exception {
        String created = upload(token(alice), SHOW_ID, JpegFixtures.jpeg(80, 40))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.eventId").value(SHOW_ID.toString()))
                .andExpect(jsonPath("$.eventTitle").value("Late night jazz"))
                .andExpect(jsonPath("$.uploader.displayName").value("Alice"))
                .andReturn().getResponse().getContentAsString();
        String photoId = JsonPath.read(created, "$.id");
        assertThat(created).doesNotContain("photos.test");
        String card = "/api/events/" + SHOW_ID + "/photos/" + photoId + "/file?variant=card";
        String full = "/api/events/" + SHOW_ID + "/photos/" + photoId + "/file?variant=full";

        upload(token(alice), SHOW_ID, JpegFixtures.jpeg(80, 40)).andExpect(status().isCreated());

        mockMvc.perform(get("/api/events/{eventId}/photos", SHOW_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(get("/api/events/{eventId}/photos/{photoId}/file", SHOW_ID, photoId)
                        .param("variant", "card"))
                .andExpect(status().isUnauthorized());
        perform(get("/api/events/{eventId}/photos/{photoId}/file", SHOW_ID, photoId).param("variant", "full"),
                token(bob)).andExpect(status().isForbidden());

        perform(get("/api/me/events/{eventId}/photos", SHOW_ID), token(alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].cardUrl").value(org.hamcrest.Matchers.startsWith("/api/events/")))
                .andExpect(jsonPath("$[1].id").value(photoId))
                .andExpect(jsonPath("$[1].cardUrl").value(card))
                .andExpect(jsonPath("$[1].fullUrl").value(full))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("photos.test"))));

        perform(post("/api/admin/photos/{photoId}/approve", photoId), adminToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.cardUrl").value("https://photos.test/event-photos/" + photoId + "/card.jpg"))
                .andExpect(jsonPath("$.fullUrl").value("https://photos.test/event-photos/" + photoId + "/full.jpg"));

        mockMvc.perform(get("/api/events/{eventId}/photos", SHOW_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(photoId))
                .andExpect(jsonPath("$[0].status").value("APPROVED"));
    }

    @Test
    void storedJpegsHaveNoGpsAndAreOnlyTheTwoDerivatives() throws Exception {
        byte[] upload = JpegFixtures.jpegWithGps(2000, 1000);
        assertThat(JpegFixtures.hasGps(upload)).isTrue();

        String photoId = id(upload(token(alice), SHOW_ID, upload).andExpect(status().isCreated()));

        assertThat(stored.keys()).containsExactlyInAnyOrder(key(photoId, "card"), key(photoId, "full"));
        assertThat(JpegFixtures.hasGps(stored.get(key(photoId, "card")))).isFalse();
        assertThat(JpegFixtures.hasGps(stored.get(key(photoId, "full")))).isFalse();
        assertThat(size(stored.get(key(photoId, "card")))).isEqualTo(new int[] {640, 320});
        assertThat(size(stored.get(key(photoId, "full")))).isEqualTo(new int[] {1600, 800});

        byte[] body = perform(get("/api/events/{eventId}/photos/{photoId}/file", SHOW_ID, photoId)
                        .param("variant", "full"), token(alice))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(JpegFixtures.hasGps(body)).isFalse();
        assertThat(size(body)).isEqualTo(new int[] {1600, 800});
    }

    @Test
    void theWrongTypeIsBadRequestAndTooBigIsContentTooLarge() throws Exception {
        upload(token(alice), SHOW_ID, JpegFixtures.text())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Only JPEG, PNG and WebP images are accepted"));
        upload(token(alice), SHOW_ID, JpegFixtures.tooBig())
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.detail").value("The file must not exceed 10 MB"));
        perform(get("/api/events/{eventId}/photos/{photoId}/file", SHOW_ID, UUID.randomUUID()).param("variant", "thumb"),
                token(alice)).andExpect(status().isBadRequest());
        perform(get("/api/admin/photos").param("status", "NOPE"), adminToken()).andExpect(status().isBadRequest());

        assertThat(stored.keys()).isEmpty();
        assertThat(photoCount()).isZero();
    }

    @Test
    void pngIsAccepted() throws Exception {
        upload(token(alice), SHOW_ID, JpegFixtures.png(16, 16))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void signedOutCallsAreUnauthorized() throws Exception {
        mockMvc.perform(uploadRequest(SHOW_ID, JpegFixtures.jpeg(8, 8))).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/me/events/{eventId}/photos", SHOW_ID)).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/events/{eventId}/photos/{photoId}", SHOW_ID, UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/events/{eventId}/photos/{photoId}/reports", SHOW_ID, UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"SPAM\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/photos")).andExpect(status().isUnauthorized());

        assertThat(photoCount()).isZero();
    }

    @Test
    void onlyAnAdminCanApproveRejectOrRemoveSomeoneElsesPhoto() throws Exception {
        String photoId = id(upload(token(alice), SHOW_ID, JpegFixtures.jpeg(8, 8)).andExpect(status().isCreated()));

        perform(get("/api/admin/photos"), token(bob)).andExpect(status().isForbidden());
        perform(post("/api/admin/photos/{photoId}/approve", photoId), token(bob)).andExpect(status().isForbidden());
        perform(post("/api/admin/photos/{photoId}/reject", photoId), token(bob)).andExpect(status().isForbidden());
        perform(delete("/api/admin/photos/{photoId}", photoId), token(bob)).andExpect(status().isForbidden());
        perform(delete("/api/events/{eventId}/photos/{photoId}", SHOW_ID, photoId), token(bob))
                .andExpect(status().isForbidden());

        assertThat(photoCount()).isEqualTo(1);
        perform(get("/api/admin/photos"), adminToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(photoId))
                .andExpect(jsonPath("$[0].status").value("PENDING"));
        perform(post("/api/admin/photos/{photoId}/reject", photoId), adminToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.cardUrl").value(file(SHOW_ID, photoId, "card")));
        mockMvc.perform(get("/api/events/{eventId}/photos", SHOW_ID))
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void theOwnerCanDeleteAndAnAdminRemoveResolvesOpenPhotoReports() throws Exception {
        String kept = id(upload(token(alice), SHOW_ID, JpegFixtures.jpeg(8, 8)).andExpect(status().isCreated()));
        String removed = id(upload(token(bob), SHOW_ID, JpegFixtures.jpeg(8, 8)).andExpect(status().isCreated()));
        perform(post("/api/admin/photos/{photoId}/approve", removed), adminToken()).andExpect(status().isOk());
        perform(report(SHOW_ID, removed, "SPAM", "Not from the night"), token(alice))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.targetType").value("PHOTO"))
                .andExpect(jsonPath("$.targetId").value(removed));

        perform(delete("/api/events/{eventId}/photos/{photoId}", SHOW_ID, kept), token(alice))
                .andExpect(status().isNoContent());
        perform(delete("/api/admin/photos/{photoId}", removed), adminToken()).andExpect(status().isNoContent());

        assertThat(photoCount()).isZero();
        assertThat(stored.keys()).isEmpty();
        assertThat(jdbc.sql("SELECT resolution FROM app.report").query(String.class).single()).isEqualTo("REMOVED");
        assertThat(jdbc.sql("SELECT resolved_at IS NOT NULL FROM app.report").query(Boolean.class).single()).isTrue();
        mockMvc.perform(get("/api/events/{eventId}/photos/{photoId}/file", SHOW_ID, removed).param("variant", "card"))
                .andExpect(status().isNotFound());
    }

    @Test
    void reportingFollowsTheCommentRulesAndOnlyAnApprovedPhotoCanBeReported() throws Exception {
        String pending = id(upload(token(alice), SHOW_ID, JpegFixtures.jpeg(8, 8)).andExpect(status().isCreated()));
        String approved = id(upload(token(alice), SHOW_ID, JpegFixtures.jpeg(8, 8)).andExpect(status().isCreated()));
        perform(post("/api/admin/photos/{photoId}/approve", approved), adminToken()).andExpect(status().isOk());

        perform(report(SHOW_ID, pending, "SPAM", null), token(bob)).andExpect(status().isNotFound());
        perform(report(UUID.randomUUID(), approved, "SPAM", null), token(bob)).andExpect(status().isNotFound());
        perform(report(SHOW_ID, UUID.randomUUID().toString(), "SPAM", null), token(bob)).andExpect(status().isNotFound());

        String first = perform(report(SHOW_ID, approved, "INAPPROPRIATE", "  blurry  "), token(bob))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.targetType").value("PHOTO"))
                .andExpect(jsonPath("$.reason").value("INAPPROPRIATE"))
                .andExpect(jsonPath("$.details").value("blurry"))
                .andReturn().getResponse().getContentAsString();
        String firstId = JsonPath.read(first, "$.id");
        perform(report(SHOW_ID, approved, "SPAM", "again"), token(bob))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(firstId))
                .andExpect(jsonPath("$.reason").value("INAPPROPRIATE"));

        perform(post("/api/admin/photos/{photoId}/reject", approved), adminToken()).andExpect(status().isOk());
        perform(report(SHOW_ID, approved, "OTHER", null), token(alice)).andExpect(status().isNotFound());
        assertThat(jdbc.sql("SELECT count(*) FROM app.report").query(Long.class).single()).isEqualTo(1);
    }

    @Test
    void theEleventhPendingOrApprovedPhotoConflictsAndARejectedOneDoesNotCount() throws Exception {
        for (int i = 0; i < 10; i++) {
            upload(token(alice), SHOW_ID, JpegFixtures.jpeg(8, 8)).andExpect(status().isCreated());
        }
        upload(token(alice), SHOW_ID, JpegFixtures.jpeg(8, 8)).andExpect(status().isConflict());
        upload(token(bob), SHOW_ID, JpegFixtures.jpeg(8, 8)).andExpect(status().isCreated());

        String rejected = jdbc.sql("""
                SELECT id::text FROM app.event_photo
                WHERE uploader_id = :userId
                ORDER BY created_at, id
                LIMIT 1
                """).param("userId", alice.getId()).query(String.class).single();
        perform(post("/api/admin/photos/{photoId}/reject", rejected), adminToken()).andExpect(status().isOk());
        upload(token(alice), SHOW_ID, JpegFixtures.jpeg(8, 8)).andExpect(status().isCreated());
        upload(token(alice), SHOW_ID, JpegFixtures.jpeg(8, 8)).andExpect(status().isConflict());
    }

    @Test
    void anUnknownOrWithdrawnEventIsNotFound() throws Exception {
        upload(token(alice), UUID.randomUUID(), JpegFixtures.jpeg(8, 8)).andExpect(status().isNotFound());

        UUID withdrawn = UUID.randomUUID();
        publish(withdrawn, withdrawn, false, "UNAVAILABLE");
        upload(token(alice), withdrawn, JpegFixtures.jpeg(8, 8))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Event not found."));
        mockMvc.perform(get("/api/events/{eventId}/photos", withdrawn)).andExpect(status().isNotFound());

        assertThat(photoCount()).isZero();
    }

    @Test
    void anOldEventIdIsTheShowAndUploadsFailClearlyWhenStorageIsOff() throws Exception {
        UUID showId = UUID.randomUUID();
        UUID oldEventId = UUID.randomUUID();
        publish(showId, oldEventId, true, "UPCOMING");

        String photoId = id(upload(token(alice), oldEventId, JpegFixtures.jpeg(8, 8)).andExpect(status().isCreated()));
        perform(get("/api/admin/photos/{photoId}", photoId), adminToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(showId.toString()));

        doThrow(new EventPhotoStorageUnavailableException()).when(storage).put(anyString(), any(byte[].class));
        upload(token(alice), SHOW_ID, JpegFixtures.jpeg(8, 8))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("Photo uploads are not available right now"));
    }

    private ResultActions upload(String token, UUID eventId, byte[] bytes) throws Exception {
        return mockMvc.perform(uploadRequest(eventId, bytes).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private static MockMultipartHttpServletRequestBuilder uploadRequest(UUID eventId, byte[] bytes) {
        return multipart("/api/events/{eventId}/photos", eventId)
                .file(new MockMultipartFile("file", "photo.jpg", "image/jpeg", bytes));
    }

    private static MockHttpServletRequestBuilder report(UUID eventId, String photoId, String reason, String details) {
        String json = details == null
                ? "{\"reason\": \"" + reason + "\"}"
                : "{\"reason\": \"" + reason + "\", \"details\": \"" + details + "\"}";
        return post("/api/events/{eventId}/photos/{photoId}/reports", eventId, photoId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json);
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, String token) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private String token(UserAccount user) {
        return accessTokenService.issueAccessToken(user);
    }

    private String adminToken() {
        Instant expiresAt = Instant.now().plus(Duration.ofMinutes(5));
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(admin.getId().toString())
                .issuedAt(expiresAt.minus(Duration.ofMinutes(1)))
                .expiresAt(expiresAt)
                .claim("roles", java.util.List.of("ADMIN"))
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    private static String id(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
    }

    private static String key(String photoId, String variant) {
        return "event-photos/" + photoId + "/" + variant + ".jpg";
    }

    private static String file(UUID eventId, String photoId, String variant) {
        return "/api/events/" + eventId + "/photos/" + photoId + "/file?variant=" + variant;
    }

    private long photoCount() {
        return jdbc.sql("SELECT count(*) FROM app.event_photo").query(Long.class).single();
    }

    private static int[] size(byte[] jpeg) throws Exception {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(jpeg));
        return new int[] {image.getWidth(), image.getHeight()};
    }

    private void publish(UUID showId, UUID eventId, boolean discoverable, String state) {
        jdbc.sql("""
                INSERT INTO publication.event_snapshot (
                    loc_occurrence_id, loc_event_id, loc_group_id, revision, discoverable, state, payload,
                    first_published_at, updated_at
                ) VALUES (
                    :occurrenceId, :eventId, :showId, 1, :discoverable, :state,
                    CAST(:payload AS jsonb), now(), now()
                )
                """)
                .param("occurrenceId", UUID.randomUUID())
                .param("eventId", eventId)
                .param("showId", showId)
                .param("discoverable", discoverable)
                .param("state", state)
                .param("payload", """
                        {"title": "Late night jazz", "location": {"type": "ONLINE"},
                         "schedule": {"starts_at": "2998-01-01T20:00:00Z"},
                         "validUntil": "2999-01-01T00:00:00Z"}""")
                .update();
    }

    private static final class RecordingStorage {
        private final Map<String, byte[]> objects = new HashMap<>();

        void bind(EventPhotoStorage storage) {
            lenient().doAnswer(invocation -> {
                objects.put(invocation.getArgument(0), ((byte[]) invocation.getArgument(1)).clone());
                return null;
            }).when(storage).put(anyString(), any(byte[].class));
            lenient().when(storage.get(anyString())).thenAnswer(invocation -> {
                byte[] found = objects.get(invocation.getArgument(0, String.class));
                return found == null ? Optional.empty() : Optional.of(found.clone());
            });
            lenient().doAnswer(invocation -> {
                objects.remove(invocation.getArgument(0, String.class));
                return null;
            }).when(storage).delete(anyString());
        }

        void clear() {
            objects.clear();
        }

        byte[] get(String key) {
            return objects.get(key);
        }

        Set<String> keys() {
            return Set.copyOf(objects.keySet());
        }
    }

    @Nested
    @SpringBootTest(properties = {
            "app.rate-limit.policies.photo-upload.capacity=2",
            "app.rate-limit.policies.photo-upload.period=1h",
            "app.rate-limit.policies.report.capacity=1",
            "app.rate-limit.policies.report.period=1h"})
    @AutoConfigureMockMvc
    @Testcontainers
    @NestedTestConfiguration(NestedTestConfiguration.EnclosingConfiguration.OVERRIDE)
    @Sql(scripts = "/db/publication-discoverable-events.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
    class PhotoRateLimit {

        private static final UUID SHOW_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");

        @Container
        @ServiceConnection
        private static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

        private static final GenericContainer<?> redis = new GenericContainer<>("redis:8.2-alpine")
                .withExposedPorts(6379);

        static {
            redis.start();
        }

        @DynamicPropertySource
        static void redisUrl(DynamicPropertyRegistry registry) {
            registry.add("spring.data.redis.url",
                    () -> "redis://" + redis.getHost() + ":" + redis.getMappedPort(6379));
        }

        @MockitoBean
        private EventPhotoStorage storage;

        @Autowired
        private MockMvc mockMvc;
        @Autowired
        private UserAccountRepository userRepository;
        @Autowired
        private AccessTokenService accessTokenService;
        @Autowired
        private JwtEncoder jwtEncoder;
        @Autowired
        private JdbcClient jdbc;

        private final RecordingStorage stored = new RecordingStorage();

        @Test
        void uploadsAndReportsAreLimitedSeparately() throws Exception {
            stored.bind(storage);
            jdbc.sql("DELETE FROM app.report").update();
            jdbc.sql("DELETE FROM app.event_photo").update();
            userRepository.deleteAll();
            jdbc.sql("DELETE FROM publication.event_snapshot").update();
            publish(SHOW_ID);
            UserAccount alice = userRepository.save(new UserAccount("rate-alice@example.com", "Alice", "hash", Instant.now()));
            UserAccount bob = userRepository.save(new UserAccount("rate-bob@example.com", "Bob", "hash", Instant.now()));
            UserAccount admin = userRepository.save(new UserAccount("rate-ada@example.com", "Ada", "hash", Instant.now()));
            String aliceToken = accessTokenService.issueAccessToken(alice);
            String bobToken = accessTokenService.issueAccessToken(bob);

            String first = id(upload(aliceToken));
            String second = id(upload(aliceToken));
            upload(aliceToken).andExpect(status().isTooManyRequests()).andExpect(header().exists(HttpHeaders.RETRY_AFTER));
            upload(bobToken).andExpect(status().isCreated());

            approve(first, admin);
            approve(second, admin);
            report(bobToken, first).andExpect(status().isCreated());
            report(bobToken, second)
                    .andExpect(status().isTooManyRequests())
                    .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
            report(aliceToken, first).andExpect(status().isCreated());
        }

        private ResultActions upload(String token) throws Exception {
            return mockMvc.perform(multipart("/api/events/{eventId}/photos", SHOW_ID)
                    .file(new MockMultipartFile("file", "photo.jpg", "image/jpeg", JpegFixtures.jpeg(8, 8)))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
        }

        private ResultActions report(String token, String photoId) throws Exception {
            return mockMvc.perform(post("/api/events/{eventId}/photos/{photoId}/reports", SHOW_ID, photoId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"reason\":\"SPAM\"}"));
        }

        private void approve(String photoId, UserAccount admin) throws Exception {
            mockMvc.perform(post("/api/admin/photos/{photoId}/approve", photoId)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken(admin)))
                    .andExpect(status().isOk());
        }

        private String adminToken(UserAccount admin) {
            Instant expiresAt = Instant.now().plus(Duration.ofMinutes(5));
            JwtClaimsSet claims = JwtClaimsSet.builder()
                    .subject(admin.getId().toString())
                    .issuedAt(expiresAt.minus(Duration.ofMinutes(1)))
                    .expiresAt(expiresAt)
                    .claim("roles", java.util.List.of("ADMIN"))
                    .build();
            return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                    .getTokenValue();
        }

        private void publish(UUID id) {
            jdbc.sql("""
                    INSERT INTO publication.event_snapshot (
                        loc_occurrence_id, loc_event_id, loc_group_id, revision, discoverable, state, payload,
                        first_published_at, updated_at
                    ) VALUES (gen_random_uuid(), :id, :id, 1, true, 'UPCOMING',
                              '{"title":"Late night jazz"}'::jsonb, now(), now())
                    """).param("id", id).update();
        }
    }
}
