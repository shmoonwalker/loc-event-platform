package nl.loc.backend.feedback.repository;

import java.util.UUID;
import nl.loc.backend.feedback.model.Feedback;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface FeedbackRepository extends JpaRepository<Feedback, UUID> {

    // open items first, and within each group the newest first
    @Query(value = """
            SELECT f FROM Feedback f
            ORDER BY CASE WHEN f.handledAt IS NULL THEN 0 ELSE 1 END, f.createdAt DESC, f.id DESC
            """,
            countQuery = "SELECT count(f) FROM Feedback f")
    Page<Feedback> findAllOpenFirst(Pageable pageable);
}
