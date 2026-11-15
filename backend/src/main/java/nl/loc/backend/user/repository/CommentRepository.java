package nl.loc.backend.user.repository;

import java.util.Optional;
import java.util.UUID;
import nl.loc.backend.user.model.Comment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentRepository extends JpaRepository<Comment, UUID> {

    // someone else's comment is simply not found, so the API answers 404 instead of 403
    Optional<Comment> findByIdAndUserId(UUID id, UUID userId);
}
