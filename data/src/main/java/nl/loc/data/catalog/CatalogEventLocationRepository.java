package nl.loc.data.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CatalogEventLocationRepository extends JpaRepository<CatalogEventLocation, Long> {
    List<CatalogEventLocation> findByEventSource(String source);
}
