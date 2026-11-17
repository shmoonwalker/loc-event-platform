package nl.loc.backend.photo.service;

public class EventPhotoStorageUnavailableException extends RuntimeException {

    public EventPhotoStorageUnavailableException() {
        super("Photo storage is not configured");
    }
}
