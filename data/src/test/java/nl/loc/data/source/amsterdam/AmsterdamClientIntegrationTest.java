package nl.loc.data.source.amsterdam;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Calls the real Amsterdam API. Set AMSTERDAM_API_KEY to enable this test. */
@SpringBootTest(properties = "loc.object-storage.enabled=false")
@EnabledIfEnvironmentVariable(named = "AMSTERDAM_API_KEY", matches = ".*\\S.*")
class AmsterdamClientIntegrationTest {

    @Autowired
    private AmsterdamClient amsterdamClient;

    @Test
    void fetchEventsReturnsAnEventCollection() {
        String response = amsterdamClient.fetchEvents();


        assertThat(response).isNotBlank();

        // Parse JSON and verify the collection exists; an empty collection is valid.
        Object events = JsonPath.parse(response).read("$._embedded.evenementen");
        assertThat(events).isInstanceOf(List.class);
    }
}
