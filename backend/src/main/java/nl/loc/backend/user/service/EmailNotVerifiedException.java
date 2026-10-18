package nl.loc.backend.user.service;

public class EmailNotVerifiedException extends RuntimeException {
    public EmailNotVerifiedException() {
        super("Email address is not verified");
    }
}