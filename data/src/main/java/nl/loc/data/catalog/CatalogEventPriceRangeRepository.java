package nl.loc.data.catalog;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CatalogEventPriceRangeRepository extends JpaRepository<CatalogEventPriceRange, Long> {
    List<CatalogEventPriceRange> findByEventOrderByRangeIndex(CatalogEvent event);
}

