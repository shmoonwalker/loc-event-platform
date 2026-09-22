package nl.loc.data.source.rvo;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import nl.loc.data.event.EventTimeSlot;
import nl.loc.data.event.LocationType;
import nl.loc.data.event.NormalizedEvent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RvoEventMapperTest {

    private final RvoEventMapper mapper = new RvoEventMapper();

    @Test
    void map_whenPhysicalDetail_mapsIdentityLocationOrganizersUrlAndTimeSlots() {
        String json = """
                {
                  "id": "12345",
                  "title": "Workshop",
                  "url": "/evenementen/workshop",
                  "organisers": ["RVO", "Ministerie van EZK"],
                  "isOnline": false,
                  "addresses": ["Prinses Beatrixlaan 2", "Gebouw A"],
                  "locality": "Utrecht",
                  "latitude": 52.0907,
                  "longitude": 5.1214,
                  "dates": [
                    {
                      "value": "2026-03-15T09:00:00+01:00",
                      "end_value": "2026-03-15T12:00:00+01:00"
                    },
                    {
                      "value": "2026-03-16T09:00:00+01:00",
                      "end_value": "2026-03-16T12:00:00+01:00"
                    }
                  ]
                }
                """;

        NormalizedEvent event = mapper.map(json, "raw/rvo/run/events/12345.json");

        assertEquals("rvo", event.source());
        assertEquals("12345", event.externalId());
        assertEquals("raw/rvo/run/events/12345.json", event.rawObjectKey());
        assertEquals("https://www.rvo.nl/evenementen/workshop", event.sourceUrl());
        assertEquals(List.of("RVO", "Ministerie van EZK"), event.organizerNames());
        assertEquals(LocationType.PHYSICAL, event.location().locationType());
        assertEquals("Utrecht", event.location().city());
        assertEquals("Prinses Beatrixlaan 2, Gebouw A", event.location().address());
        assertEquals(52.0907, event.location().latitude());
        assertEquals(5.1214, event.location().longitude());
        assertEquals(
                List.of(
                        new EventTimeSlot(
                                Instant.parse("2026-03-15T08:00:00Z"),
                                Instant.parse("2026-03-15T11:00:00Z")
                        ),
                        new EventTimeSlot(
                                Instant.parse("2026-03-16T08:00:00Z"),
                                Instant.parse("2026-03-16T11:00:00Z")
                        )
                ),
                event.timeSlots()
        );
    }

    @Test
    void map_whenOnlineWithCoordinates_clearsLatitudeAndLongitude() {
        String json = """
                {
                  "id": "online-1",
                  "isOnline": true,
                  "latitude": 52.0907,
                  "longitude": 5.1214
                }
                """;

        NormalizedEvent event = mapper.map(json, "raw/rvo/run/events/online-1.json");

        assertEquals(LocationType.ONLINE, event.location().locationType());
        assertNull(event.location().latitude());
        assertNull(event.location().longitude());
    }

    @Test
    void map_whenIdMissing_throwsIllegalArgumentException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> mapper.map("{\"title\":\"Workshop\"}", "raw/rvo/run/events/missing.json")
        );
    }

    @Test
    void map_whenDatesEmptyOrMissing_returnsEmptyTimeSlots() {
        NormalizedEvent emptyDates = mapper.map(
                "{\"id\":\"empty-dates\",\"dates\":[]}",
                "raw/rvo/run/events/empty-dates.json"
        );
        NormalizedEvent missingDates = mapper.map(
                "{\"id\":\"no-dates\"}",
                "raw/rvo/run/events/no-dates.json"
        );

        assertNotNull(emptyDates.timeSlots());
        assertEquals(List.of(), emptyDates.timeSlots());
        assertNotNull(missingDates.timeSlots());
        assertEquals(List.of(), missingDates.timeSlots());
    }
}
