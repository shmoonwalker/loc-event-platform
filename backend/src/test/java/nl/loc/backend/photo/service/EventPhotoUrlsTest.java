package nl.loc.backend.photo.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import nl.loc.backend.photo.model.PhotoStatus;
import org.junit.jupiter.api.Test;

class EventPhotoUrlsTest {

    private static final UUID EVENT_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID PHOTO_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");

    @Test
    void onlyAnApprovedPhotoUsesThePublicUrl() {
        EventPhotoUrls urls = new EventPhotoUrls("https://photos.example/");

        assertThat(urls.card(EVENT_ID, PHOTO_ID, PhotoStatus.PENDING))
                .isEqualTo(file("card"))
                .doesNotContain("photos.example");
        assertThat(urls.full(EVENT_ID, PHOTO_ID, PhotoStatus.REJECTED)).isEqualTo(file("full"));
        assertThat(urls.card(EVENT_ID, PHOTO_ID, PhotoStatus.APPROVED))
                .isEqualTo("https://photos.example/event-photos/" + PHOTO_ID + "/card.jpg");
        assertThat(urls.full(EVENT_ID, PHOTO_ID, PhotoStatus.APPROVED))
                .isEqualTo("https://photos.example/event-photos/" + PHOTO_ID + "/full.jpg");
    }

    @Test
    void anApprovedPhotoUsesTheFileRouteWhenNoPublicUrlIsSet() {
        EventPhotoUrls urls = new EventPhotoUrls("");

        assertThat(urls.card(EVENT_ID, PHOTO_ID, PhotoStatus.APPROVED)).isEqualTo(file("card"));
        assertThat(urls.full(EVENT_ID, PHOTO_ID, PhotoStatus.APPROVED)).isEqualTo(file("full"));
    }

    private static String file(String variant) {
        return "/api/events/" + EVENT_ID + "/photos/" + PHOTO_ID + "/file?variant=" + variant;
    }
}
