package nl.loc.backend.user.service;

import java.util.UUID;
import nl.loc.backend.user.mail.AccountMailer;
import nl.loc.backend.user.model.UserAccount;
import nl.loc.backend.user.repository.UserAccountRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
public class EmailChangeService {
    private final AccountSecurityGuard guard;
    private final UserAccountRepository users;
    private final EmailVerificationService verification;
    private final ApplicationEventPublisher events;
    private final AccountMailer mailer;

    public EmailChangeService(AccountSecurityGuard guard, UserAccountRepository users,
                              EmailVerificationService verification, ApplicationEventPublisher events,
                              AccountMailer mailer) {
        this.guard = guard;
        this.users = users;
        this.verification = verification;
        this.events = events;
        this.mailer = mailer;
    }

    @Transactional
    public void request(UUID userId, String currentPassword, String newEmail) {
        UserAccount user = guard.requireCurrentPassword(userId, currentPassword);
        String address = UserAccount.normalizeEmail(newEmail);
        // Supersede the previous link even for a taken address, so its usability
        // cannot be used to discover whether the new address has an account.
        if (users.existsByEmail(address)) {
            verification.invalidateUnused(user);
            return;
        }
        String token = verification.issueToken(user, address);
        events.publishEvent(new LinkRequested(userId, address, token));
    }

    record LinkRequested(UUID userId, String address, String token) {}
    record EmailChanged(UUID userId, String oldAddress) {}

    @TransactionalEventListener
    public void sendLink(LinkRequested request) {
        mailer.sendEmailChangeLink(request.userId(), request.address(), request.token());
    }

    @TransactionalEventListener
    public void notifyPreviousAddress(EmailChanged change) {
        mailer.sendEmailChanged(change.userId(), change.oldAddress());
    }
}
