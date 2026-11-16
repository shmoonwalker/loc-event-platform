package nl.loc.backend.feedback.repository;

import java.util.UUID;
import nl.loc.backend.feedback.model.Feedback;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedbackRepository extends JpaRepository<Feedback, UUID> {
}
