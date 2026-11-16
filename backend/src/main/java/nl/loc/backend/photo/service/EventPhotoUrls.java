package nl.loc.backend.photo.service;

import java.util.UUID;
import nl.loc.backend.photo.model.PhotoStatus;
import nl.loc.backend.photo.model.PhotoVariant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * URLs handed to the client. A public storage URL is used only for an approved photo, and only when
 * {@code EVENT_PHOTO_PUBLIC_URL} is set. Every other photo points at the file route.
 */
@Component
public class EventPhotoUrls {

    private final String publicUrl;

    public EventPhotoUrls(@Value("${app.event-photo.storage.public-url:}") String publicUrl) {
        this.publicUrl = publicUrl.endsWith("/") ? publicUrl.substring(0, publicUrl.length() - 1) : publicUrl;
    }

    public String card(UUID eventId, UUID photoId, PhotoStatus status) {
        return url(eventId, photoId, status, PhotoVariant.CARD);
    }

    public String full(UUID eventId, UUID photoId, PhotoStatus status) {
        return url(eventId, photoId, status, PhotoVariant.FULL);
    }

    private String url(UUID eventId, UUID photoId, PhotoStatus status, PhotoVariant variant) {
        if (status == PhotoStatus.APPROVED && !publicUrl.isBlank()) {
            return publicUrl + "/" + EventPhotoStorage.key(photoId, variant);
        }
        return "/api/events/" + eventId + "/photos/" + photoId + "/file?variant=" + variant.param();
    }
}
