package nl.loc.data.catalog;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CatalogEventImageRepository extends JpaRepository<CatalogEventImage, Long> {
    List<CatalogEventImage> findByEventOrderByDisplayOrder(CatalogEvent event);
}

