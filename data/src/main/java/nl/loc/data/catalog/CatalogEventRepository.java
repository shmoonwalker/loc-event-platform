package nl.loc.data.catalog;


import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;

public interface CatalogEventRepository extends JpaRepository<CatalogEvent, Long> {
    Optional<CatalogEvent> findBySourceAndExternalId(String source, String externalId);
    List<CatalogEvent> findBySource(String source);
}
