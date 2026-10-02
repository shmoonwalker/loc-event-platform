package nl.loc.data.catalog;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CatalogEventOrganizerRepository extends JpaRepository<CatalogEventOrganizer, Long> {
    List<CatalogEventOrganizer> findByEventOrderByOrganizerIndex(CatalogEvent event);
}
