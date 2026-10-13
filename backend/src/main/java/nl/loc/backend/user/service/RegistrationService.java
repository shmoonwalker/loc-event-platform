package nl.loc.backend.user.service;

import java.time.Clock;
import java.time.Instant;

import org.hibernate.exception.ConstraintViolationException;
import nl.loc.backend.user.model.UserAccount;
import nl.loc.backend.user.repository.UserAccountRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class RegistrationService {

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private static final String EMAIL_UNIQUE_CONSTRAINT = "app_user_email_key";

    public RegistrationService(UserAccountRepository userAccountRepository, PasswordEncoder passwordEncoder, Clock clock) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    public void register(String email, String password, String displayName) {
        String normalizedEmail = UserAccount.normalizeEmail(email);
        // hash before the duplicate check so new and existing emails take the same time
        String passwordHash = passwordEncoder.encode(password);
        if (userAccountRepository.existsByEmail(normalizedEmail)) {
            return;
        }
        try {
            userAccountRepository.saveAndFlush(new UserAccount
                    (normalizedEmail, displayName, passwordHash, Instant.now(clock)));
        } catch (DataIntegrityViolationException ex) {
            if (!isDuplicateEmail(ex)) {
                throw ex;
            }
        }
        }

        private static boolean isDuplicateEmail(DataIntegrityViolationException ex) {
            return ex.getCause() instanceof ConstraintViolationException cve
                    && EMAIL_UNIQUE_CONSTRAINT.equals(cve.getConstraintName());
        }
}
