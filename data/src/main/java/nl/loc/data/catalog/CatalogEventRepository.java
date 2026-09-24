package nl.loc.data.catalog;


import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CatalogEventRepository extends JpaRepository<CatalogEvent, Long> {
    Optional<CatalogEvent> findBySourceAndExternalId(String source, String externalId);
}
