package nl.loc.backend.event.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import nl.loc.backend.event.model.TimeWindow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Sql(scripts = "/db/publication-discoverable-events.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class PublicEventsHttpTest {

    private static final Set<String> PAGE_KEYS = Set.of("page", "size", "totalElements", "totalPages", "items");
    private static final Set<String> CARD_KEYS = Set.of(
            "id", "title", "startAt", "endAt", "citySlug", "cityName", "venueName", "imageUrl", "categories", "tags");
    private static final Set<String> FORBIDDEN_KEYS = Set.of(
            "source", "sourceUrl", "locOccurrenceId", "locEventId", "organizers", "organizer",
            "externalId", "external_id", "revision", "lifecycle", "discoverable", "state",
            "validUntil", "description", "locOrganizerId", "name");
    private static final List<String> HIDDEN_TEXT = List.of(
            "Hidden Organizer",
            "hidden-link",
            "tm-998877",
            "ext-org-secret",
            "ticketmaster",
            "SCHEDULED",
            "Withdrawn Secret Show",
            "Expired Secret Show",
            "Internal description that stays off the card");

    private static final UUID MUSIC_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID SPORTS_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID ARTS_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID ONLINE_ID = UUID.fromString("44444444-4444-4444-8444-444444444444");
    private static final UUID TIE_LOW_ID = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID TIE_HIGH_ID = UUID.fromString("00000000-0000-4000-8000-000000000002");
    private static final UUID LATER_ID = UUID.fromString("55555555-5555-4555-8555-555555555555");
    private static final UUID WITHDRAWN_ID = UUID.fromString("66666666-6666-4666-8666-666666666666");
    private static final UUID EXPIRED_ID = UUID.fromString("77777777-7777-4777-8777-777777777777");

    @Container
    @ServiceConnection
    private static PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));

    private final MockMvc mockMvc;
    private final JdbcClient jdbc;
    private final List<UUID> hiddenOccurrenceIds = new ArrayList<>();
    private final List<Seeded> discoverable = new ArrayList<>();

    private Seeded music;
    private Seeded sports;
    private Seeded arts;
    private Seeded online;
    private Seeded tieLow;
    private Instant tieStart;

    PublicEventsHttpTest(MockMvc mockMvc, JdbcClient jdbc) {
        this.mockMvc = mockMvc;
        this.jdbc = jdbc;
    }

    @BeforeEach
    void seedDiscoverableSnapshots() {
        jdbc.sql("DELETE FROM publication.event_snapshot").update();
        hiddenOccurrenceIds.clear();
        discoverable.clear();
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant validUntil = now.plus(Duration.ofDays(400));
        TimeWindow.Range tonight = TimeWindow.tonight(now);
        TimeWindow.Range weekend = TimeWindow.weekend(now);

        Instant musicStart = soon(tonight, now, Duration.ofMinutes(2));
        Instant sportsStart = soon(tonight, now, Duration.ofMinutes(20));
        if (!sportsStart.isAfter(musicStart)) {
            sportsStart = musicStart.plusSeconds(1);
            if (!sportsStart.isBefore(tonight.startsBefore())) {
                sportsStart = musicStart;
            }
        }
        Instant artsStart = inWeekend(now, tonight, weekend);
        Instant onlineStart = upcomingWeekday(now);
        tieStart = now.plus(Duration.ofDays(3));
        Instant laterStart = now.plus(Duration.ofDays(40));

        music = insert(MUSIC_ID, "Late night jazz", musicStart, "Music & Nightlife", "jazz",
                "PHYSICAL", "Amsterdam", "Paradiso", "https://images.example/jazz.jpg", true, validUntil);
        sports = insert(SPORTS_ID, "Evening football", sportsStart, "Sports", "football",
                "PHYSICAL", "Rotterdam", "De Kuip", null, true, validUntil);
        arts = insert(ARTS_ID, "Gallery opening", artsStart, "Arts & Culture", "theatre",
                "PHYSICAL", "Amsterdam", "Rijksmuseum", null, true, validUntil);
        online = insert(ONLINE_ID, "Remote coding hour", onlineStart, "Technology & Science", "meetup",
                "ONLINE", null, null, null, true, validUntil);
        tieLow = insert(TIE_LOW_ID, "Shared start alpha", tieStart, "Other", "coding",
                "PHYSICAL", "Amsterdam", "Melkweg", null, true, validUntil);
        Seeded tieHigh = insert(TIE_HIGH_ID, "Shared start beta", tieStart, "Other", "coding",
                "PHYSICAL", "Amsterdam", "Melkweg", null, true, validUntil);
        Seeded later = insert(LATER_ID, "Far future walk", laterStart, "Nature & Sustainability", "nature",
                "PHYSICAL", "Utrecht", "Griftpark", null, true, validUntil);
        insert(WITHDRAWN_ID, "Withdrawn Secret Show", musicStart, "Other", "other",
                "PHYSICAL", "Amsterdam", "Paradiso", null, false, validUntil);
        insert(EXPIRED_ID, "Expired Secret Show", musicStart, "Other", "other",
                "PHYSICAL", "Amsterdam", "Paradiso", null, true, now.minus(Duration.ofDays(2)));
        discoverable.add(music);
        discoverable.add(sports);
        discoverable.add(arts);
        discoverable.add(online);
        discoverable.add(tieLow);
        discoverable.add(tieHigh);
        discoverable.add(later);
    }

    @Test
    void categoryFilterMatchesAnyRepeatedCategory() throws Exception {
        ResultActions result = mockMvc.perform(events().param("category", "music-nightlife", "sports"));

        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.items[*].id", hasItem(music.id().toString())))
                .andExpect(jsonPath("$.items[*].id", hasItem(sports.id().toString())))
                .andExpect(jsonPath("$.items[*].id", not(hasItem(arts.id().toString()))));
        assertSnapshotInternalsStayOffTheResponse(body(result));
    }

    @Test
    void dateFilterLimitsTonightWeekendAndUpcoming() throws Exception {
        ResultActions tonight = mockMvc.perform(events().param("when", "tonight"));
        tonight.andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id", hasItem(music.id().toString())))
                .andExpect(jsonPath("$.items[*].id", hasItem(sports.id().toString())))
                .andExpect(jsonPath("$.items[*].id", not(hasItem(online.id().toString()))))
                .andExpect(jsonPath("$.items[*].id", not(hasItem(LATER_ID.toString()))))
                .andExpect(jsonPath("$.items[*].id", not(hasItem(tieLow.id().toString()))));

        ResultActions weekend = mockMvc.perform(events().param("when", "weekend"));
        weekend.andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id", hasItem(arts.id().toString())))
                .andExpect(jsonPath("$.items[*].id", not(hasItem(online.id().toString()))))
                .andExpect(jsonPath("$.items[*].id", not(hasItem(LATER_ID.toString()))));

        ResultActions upcoming = mockMvc.perform(events().param("when", "upcoming"));
        upcoming.andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id", hasItem(music.id().toString())))
                .andExpect(jsonPath("$.items[*].id", hasItem(online.id().toString())))
                .andExpect(jsonPath("$.items[*].id", not(hasItem(LATER_ID.toString()))));
    }

    @Test
    void locationFilterUsesPlaceAndCitySlug() throws Exception {
        mockMvc.perform(events().param("place", "physical"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id", hasItem(music.id().toString())))
                .andExpect(jsonPath("$.items[*].id", not(hasItem(online.id().toString()))));

        mockMvc.perform(events().param("place", "online"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id").value(online.id().toString()))
                .andExpect(jsonPath("$.items[0].citySlug").value(nullValue()))
                .andExpect(jsonPath("$.items[0].cityName").value(nullValue()))
                .andExpect(jsonPath("$.items[0].venueName").value(nullValue()));

        mockMvc.perform(events().param("place", "physical").param("city", "amsterdam"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id", hasItem(music.id().toString())))
                .andExpect(jsonPath("$.items[*].id", hasItem(arts.id().toString())))
                .andExpect(jsonPath("$.items[*].id", not(hasItem(sports.id().toString()))))
                .andExpect(jsonPath("$.items[*].id", not(hasItem(online.id().toString()))))
                .andExpect(jsonPath("$.items[*].id", not(hasItem(LATER_ID.toString()))));
    }

    @Test
    void paginationStartsAtZeroAndReturnsTheNextPage() throws Exception {
        assertThat(discoverable).hasSize(7);
        List<String> expectedIds = discoverable.stream()
                .sorted(Comparator.comparing(Seeded::start).thenComparing(Seeded::id))
                .map(seeded -> seeded.id().toString())
                .toList();

        ResultActions first = mockMvc.perform(events().param("page", "0").param("size", "2"));
        first.andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(7))
                .andExpect(jsonPath("$.totalPages").value(4))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].id").value(expectedIds.get(0)))
                .andExpect(jsonPath("$.items[1].id").value(expectedIds.get(1)));

        mockMvc.perform(events().param("page", "1").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(7))
                .andExpect(jsonPath("$.totalPages").value(4))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].id").value(expectedIds.get(2)))
                .andExpect(jsonPath("$.items[1].id").value(expectedIds.get(3)));
        assertSnapshotInternalsStayOffTheResponse(body(first));
    }

    @Test
    void eventCardAndPageExposeOnlyPublicFields() throws Exception {
        ResultActions result = mockMvc.perform(events()
                .param("category", "music-nightlife")
                .param("city", "amsterdam")
                .param("place", "physical"));

        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id").value(music.id().toString()))
                .andExpect(jsonPath("$.items[0].title").value("Late night jazz"))
                .andExpect(jsonPath("$.items[0].startAt").value(music.start().toString()))
                .andExpect(jsonPath("$.items[0].endAt").value(music.start().plus(Duration.ofHours(2)).toString()))
                .andExpect(jsonPath("$.items[0].citySlug").value("amsterdam"))
                .andExpect(jsonPath("$.items[0].cityName").value("Amsterdam"))
                .andExpect(jsonPath("$.items[0].venueName").value("Paradiso"))
                .andExpect(jsonPath("$.items[0].imageUrl").value("https://images.example/jazz.jpg"))
                .andExpect(jsonPath("$.items[0].categories", hasSize(1)))
                .andExpect(jsonPath("$.items[0].categories[0]").value("music-nightlife"))
                .andExpect(jsonPath("$.items[0].tags", hasSize(1)))
                .andExpect(jsonPath("$.items[0].tags[0]").value("jazz"));

        JsonNode root = JsonMapper.shared().readTree(body(result));
        assertThat(names(root)).containsExactlyInAnyOrderElementsOf(PAGE_KEYS);
        assertThat(names(root.get("items").get(0))).containsExactlyInAnyOrderElementsOf(CARD_KEYS);
        assertSnapshotInternalsStayOffTheResponse(body(result));
    }

    @Test
    void equalStartTimesStayOrderedByEventId() throws Exception {
        ResultActions first = mockMvc.perform(events().param("tag", "coding").param("sort", "start_time"));
        first.andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].id").value(TIE_LOW_ID.toString()))
                .andExpect(jsonPath("$.items[1].id").value(TIE_HIGH_ID.toString()))
                .andExpect(jsonPath("$.items[0].startAt").value(tieStart.toString()))
                .andExpect(jsonPath("$.items[1].startAt").value(tieStart.toString()));

        MvcResult second = mockMvc.perform(events().param("tag", "coding").param("sort", "start_time"))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(second.getResponse().getContentAsString()).isEqualTo(body(first));
    }

    @Test
    void filtersThatMatchNothingReturnAnEmptyPage() throws Exception {
        mockMvc.perform(events().param("city", "groningen"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0))
                .andExpect(jsonPath("$.items", hasSize(0)));
    }

    private MockHttpServletRequestBuilder events() {
        return get("/api/events");
    }

    private static String body(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString();
    }

    private void assertSnapshotInternalsStayOffTheResponse(String body) {
        for (String hidden : HIDDEN_TEXT) {
            assertThat(body).doesNotContain(hidden);
        }
        for (UUID occurrenceId : hiddenOccurrenceIds) {
            assertThat(body).doesNotContain(occurrenceId.toString());
        }
        assertThat(body).doesNotContain(WITHDRAWN_ID.toString());
        assertThat(body).doesNotContain(EXPIRED_ID.toString());
        walk(JsonMapper.shared().readTree(body));
    }

    private void walk(JsonNode node) {
        if (node.isObject()) {
            for (java.util.Map.Entry<String, JsonNode> property : node.properties()) {
                assertThat(property.getKey()).isNotIn(FORBIDDEN_KEYS);
                walk(property.getValue());
            }
            return;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                walk(child);
            }
        }
    }

    private static Set<String> names(JsonNode node) {
        return Set.copyOf(node.propertyNames());
    }

    private Seeded insert(
            UUID eventId,
            String title,
            Instant start,
            String category,
            String tag,
            String place,
            String city,
            String venue,
            String imageUrl,
            boolean discoverableRow,
            Instant validUntil) {
        UUID occurrenceId = UUID.randomUUID();
        hiddenOccurrenceIds.add(occurrenceId);
        Instant end = start.plus(Duration.ofHours(2));
        String payload = payload(eventId, occurrenceId, title, start, end, category, tag, place, city, venue, imageUrl, validUntil);
        jdbc.sql("""
                INSERT INTO publication.event_snapshot (
                    loc_occurrence_id, loc_event_id, revision, discoverable, state, payload,
                    first_published_at, updated_at
                ) VALUES (
                    :occurrenceId, :eventId, 1, :discoverable, 'PUBLISHED', CAST(:payload AS jsonb),
                    :publishedAt, :updatedAt
                )
                """)
                .param("occurrenceId", occurrenceId)
                .param("eventId", eventId)
                .param("discoverable", discoverableRow)
                .param("payload", payload)
                .param("publishedAt", start.atOffset(ZoneOffset.UTC))
                .param("updatedAt", start.atOffset(ZoneOffset.UTC))
                .update();
        return new Seeded(eventId, start);
    }

    private static String payload(
            UUID eventId,
            UUID occurrenceId,
            String title,
            Instant start,
            Instant end,
            String category,
            String tag,
            String place,
            String city,
            String venue,
            String imageUrl,
            Instant validUntil) {
        String images = imageUrl == null ? "[]" : "[{\"url\":\"" + imageUrl + "\"}]";
        return """
                {
                  "title": "%s",
                  "description": "Internal description that stays off the card",
                  "source": "ticketmaster",
                  "sourceUrl": "https://provider.example/hidden-link",
                  "locEventId": "%s",
                  "locOccurrenceId": "%s",
                  "externalId": "tm-998877",
                  "lifecycle": "SCHEDULED",
                  "revision": 4,
                  "discoverable": true,
                  "state": "PUBLISHED",
                  "categories": ["%s"],
                  "tags": ["%s"],
                  "schedule": {"starts_at": "%s", "ends_at": "%s"},
                  "location": %s,
                  "images": %s,
                  "organizers": [{
                    "locOrganizerId": "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
                    "name": "Hidden Organizer",
                    "externalId": "ext-org-secret"
                  }],
                  "validUntil": "%s"
                }
                """.formatted(
                title,
                eventId,
                occurrenceId,
                category,
                tag,
                start,
                end,
                locationJson(place, city, venue),
                images,
                validUntil);
    }

    private static String locationJson(String place, String city, String venue) {
        StringBuilder location = new StringBuilder();
        location.append("{\"type\":\"").append(place).append('"');
        if (city != null) {
            location.append(",\"city\":\"").append(city).append('"');
        }
        if (venue != null) {
            location.append(",\"venue_name\":\"").append(venue).append('"');
        }
        location.append('}');
        return location.toString();
    }

    private static Instant soon(TimeWindow.Range tonight, Instant now, Duration lead) {
        Instant start = now.plus(lead);
        if (start.isBefore(tonight.startsBefore())) {
            return start;
        }
        Instant fallback = tonight.startsBefore().minusSeconds(1);
        if (fallback.isAfter(now)) {
            return fallback;
        }
        return now.plusMillis(200);
    }

    private static Instant inWeekend(Instant now, TimeWindow.Range tonight, TimeWindow.Range weekend) {
        Instant afterTonight = tonight.startsBefore().plusHours(3);
        if (afterTonight.isAfter(now)
                && !afterTonight.isBefore(weekend.startsFrom())
                && afterTonight.isBefore(weekend.startsBefore())) {
            return afterTonight;
        }
        Instant start = now.plus(Duration.ofHours(1));
        if (start.isBefore(weekend.startsFrom())) {
            start = weekend.startsFrom().plusHours(3);
        }
        if (!start.isBefore(weekend.startsBefore())) {
            start = weekend.startsBefore().minusMinutes(30);
        }
        if (!start.isAfter(now)) {
            return now.plusSeconds(30);
        }
        return start;
    }

    private static Instant upcomingWeekday(Instant now) {
        ZonedDateTime local = now.atZone(TimeWindow.ZONE);
        for (int day = 2; day <= 20; day++) {
            ZonedDateTime candidate = local.plusDays(day).withHour(15).withMinute(0).withSecond(0).withNano(0);
            Instant start = candidate.toInstant();
            TimeWindow.Range weekend = TimeWindow.weekend(start);
            boolean inWeekend = !start.isBefore(weekend.startsFrom()) && start.isBefore(weekend.startsBefore());
            if (!inWeekend && start.isAfter(now)) {
                return start;
            }
        }
        return now.plus(Duration.ofDays(9));
    }

    private record Seeded(UUID id, Instant start) {
    }
}
