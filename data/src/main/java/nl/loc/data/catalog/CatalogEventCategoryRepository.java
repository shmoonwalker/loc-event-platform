package nl.loc.data.catalog;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CatalogEventCategoryRepository extends JpaRepository<CatalogEventCategory, CatalogEventCategoryKey> {
    List<CatalogEventCategory> findByEvent(CatalogEvent event);
}
