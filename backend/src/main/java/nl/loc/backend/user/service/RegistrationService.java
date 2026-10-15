package nl.loc.backend.user.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;
import nl.loc.backend.user.mail.AccountMailer;
import org.hibernate.exception.ConstraintViolationException;
import nl.loc.backend.user.model.UserAccount;
import nl.loc.backend.user.repository.UserAccountRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class RegistrationService {

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private static final String EMAIL_UNIQUE_CONSTRAINT = "app_user_email_key";
    private final EmailVerificationService emailVerificationService;
    private final AccountMailer accountMailer;

    public RegistrationService(UserAccountRepository userAccountRepository, PasswordEncoder passwordEncoder,
                               Clock clock, EmailVerificationService emailVerificationService,
                               AccountMailer accountMailer) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.emailVerificationService = emailVerificationService;
        this.accountMailer = accountMailer;
    }

    // not @Transactional: each DB step commits before the @Async mail starts
    public void register(String email, String password, String displayName) {
        String normalizedEmail = UserAccount.normalizeEmail(email);
        // hash before the duplicate check so new and existing emails take the same time
        String passwordHash = passwordEncoder.encode(password);
        Optional<UserAccount> existing = userAccountRepository.findByEmail(normalizedEmail);
        if (existing.isPresent()) {
            registerAgain(existing.get(), passwordHash, displayName);
            return;
        }

        UserAccount user;
        try {
            user = userAccountRepository.saveAndFlush(
                    new UserAccount(normalizedEmail, displayName, passwordHash, Instant.now(clock)));
        } catch (DataIntegrityViolationException ex) {
            if (!isDuplicateEmail(ex)) {
                throw ex;
            }
            return; // a parallel registration won the race and sends the mail
        }
        log.info("Registered new account {}", user.getId());
        sendVerification(user);
    }

    // unknown and verified emails get nothing, so callers can't tell the cases apart
    public void resendVerification(String email) {
        userAccountRepository.findByEmail(UserAccount.normalizeEmail(email))
                .filter(user -> !user.isEmailVerified())
                .ifPresent(user -> {
                    log.info("Resending verification link to account {}", user.getId());
                    sendVerification(user);
                });
    }

    private static boolean isDuplicateEmail(DataIntegrityViolationException ex) {
        return ex.getCause() instanceof ConstraintViolationException cve
                && EMAIL_UNIQUE_CONSTRAINT.equals(cve.getConstraintName());
    }

    private void registerAgain(UserAccount user, String passwordHash, String displayName) {
        if (user.isEmailVerified()) {
            log.info("Sign-up attempt for verified account {}", user.getId());
            accountMailer.sendSignUpAttempt(user);
            return;
        }
        user.replaceRegistration(passwordHash, displayName);
        userAccountRepository.save(user);
        log.info("Replaced unverified registration of account {}", user.getId());
        sendVerification(user);
    }

    private void sendVerification(UserAccount user) {
        String rawToken = emailVerificationService.issueToken(user);
        accountMailer.sendVerificationLink(user, rawToken);
    }
}
