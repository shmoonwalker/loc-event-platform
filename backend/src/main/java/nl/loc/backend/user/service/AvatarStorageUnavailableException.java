package nl.loc.backend.user.service;

public class AvatarStorageUnavailableException extends RuntimeException {

    public AvatarStorageUnavailableException() {
        super("Avatar storage is not configured");
    }
}
