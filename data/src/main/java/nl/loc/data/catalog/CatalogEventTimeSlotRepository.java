package nl.loc.data.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CatalogEventTimeSlotRepository extends JpaRepository<CatalogEventTimeSlot, Long> {
    List<CatalogEventTimeSlot> findByEventOrderBySlotIndex(CatalogEvent event);
}