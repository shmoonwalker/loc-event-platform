package nl.loc.backend.user.service;

public class AccountNotFoundException extends RuntimeException {
    public AccountNotFoundException() {
        super("The account of this access token no longer exists");
    }
}
