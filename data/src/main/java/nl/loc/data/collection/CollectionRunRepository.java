package nl.loc.data.collection;

import java.util.UUID;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CollectionRunRepository extends JpaRepository<CollectionRun, UUID> {
    List<CollectionRun> findBySourceAndStatusOrderByStartedAtDescIdDesc(String source, String status);
}
