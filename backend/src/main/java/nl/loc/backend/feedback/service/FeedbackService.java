package nl.loc.backend.feedback.service;

import java.time.Clock;
import java.util.UUID;
import nl.loc.backend.feedback.dto.response.FeedbackResponse;
import nl.loc.backend.feedback.model.Feedback;
import nl.loc.backend.feedback.repository.FeedbackRepository;
import nl.loc.backend.user.repository.UserAccountRepository;
import nl.loc.backend.user.service.AccountNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class FeedbackService {

    private final FeedbackRepository feedbackRepository;
    private final UserAccountRepository userAccountRepository;
    private final Clock clock;

    public FeedbackService(FeedbackRepository feedbackRepository, UserAccountRepository userAccountRepository,
                           Clock clock) {
        this.feedbackRepository = feedbackRepository;
        this.userAccountRepository = userAccountRepository;
        this.clock = clock;
    }

    /** {@code userId} is null for a visitor. A token whose account is gone is rejected, not stored as anonymous. */
    public FeedbackResponse submit(UUID userId, String message, String contactEmail) {
        if (userId != null && !userAccountRepository.existsById(userId)) {
            throw new AccountNotFoundException();
        }
        Feedback feedback = feedbackRepository.save(new Feedback(userId, message, contactEmail, clock.instant()));
        return FeedbackResponse.from(feedback);
    }
}
