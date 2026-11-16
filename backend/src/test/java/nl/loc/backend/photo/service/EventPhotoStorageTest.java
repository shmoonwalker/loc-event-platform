package nl.loc.backend.photo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class EventPhotoStorageTest {

    @Test
    void uploadsFailWhenNoBucketIsConfigured() {
        EventPhotoStorage storage = new EventPhotoStorage("", "auto", "", "", "", "", "", "");

        assertThat(storage.acceptsUploads()).isFalse();
        assertThatThrownBy(() -> storage.put("event-photos/x/card.jpg", new byte[] {1}))
                .isInstanceOf(EventPhotoStorageUnavailableException.class)
                .hasMessageContaining("not configured");
    }

    @Test
    void anUnsetEventPhotoBucketUsesTheAvatarBucket() {
        EventPhotoStorage storage = storage("photo-key", "photo-secret", "", "avatar-bucket");

        assertThat(storage.configuredBucket()).isEqualTo("avatar-bucket");
        assertThat(storage.acceptsUploads()).isTrue();
        storage.close();
    }

    @Test
    void aConfiguredEventPhotoBucketIsUsedAsIs() {
        EventPhotoStorage storage = storage("photo-key", "photo-secret", "photo-bucket", "avatar-bucket");

        assertThat(storage.configuredBucket()).isEqualTo("photo-bucket");
        storage.close();
    }

    private static EventPhotoStorage storage(String accessKey, String secretKey, String bucket, String avatarBucket) {
        return new EventPhotoStorage(
                "http://127.0.0.1:9", "auto",
                accessKey, secretKey, bucket,
                "avatar-key", "avatar-secret", avatarBucket);
    }
}
