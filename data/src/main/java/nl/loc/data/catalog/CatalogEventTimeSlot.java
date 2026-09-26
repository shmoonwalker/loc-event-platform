package nl.loc.data.catalog;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import nl.loc.data.event.DateStatus;
import nl.loc.data.event.EventTimeSlot;
import nl.loc.data.event.TimeStatus;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "event_time_slot",
        schema = "catalog",
        uniqueConstraints = @UniqueConstraint(
                name = "event_time_slot_event_index_key",
                columnNames = {"event_id", "slot_index"}
        )
)
public class CatalogEventTimeSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "event_id", nullable = false, updatable = false)
    private CatalogEvent event;

    @Column(name = "slot_index", nullable = false, updatable = false)
    private int slotIndex;

    @Column(name = "starts_at")
    private Instant startsAt;

    @Column(name = "ends_at")
    private Instant endsAt;

    @Column(name = "local_start_date")
    private LocalDate localStartDate;

    @Column(name = "local_start_time")
    private LocalTime localStartTime;

    @Column(name = "local_end_date")
    private LocalDate localEndDate;

    @Column(name = "local_end_time")
    private LocalTime localEndTime;

    @Column(name = "timezone")
    private String timezone;

    @Enumerated(EnumType.STRING)
    @Column(name = "start_date_status", nullable = false)
    private DateStatus startDateStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "start_time_status", nullable = false)
    private TimeStatus startTimeStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "end_date_status", nullable = false)
    private DateStatus endDateStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "end_time_status", nullable = false)
    private TimeStatus endTimeStatus;

    @Column(name = "end_approximate", nullable = false)
    private boolean endApproximate;

    public CatalogEventTimeSlot(
            CatalogEvent event,
            int slotIndex,
            EventTimeSlot slot
    ) {
        this.event = require(event, "event");
        this.slotIndex = slotIndex;
        updateFromImport(slot);
    }

    public void updateFromImport(EventTimeSlot slot) {
        this.startsAt = slot.startsAt();
        this.endsAt = slot.endsAt();
        this.localStartDate = slot.localStartDate();
        this.localStartTime = slot.localStartTime();
        this.localEndDate = slot.localEndDate();
        this.localEndTime = slot.localEndTime();
        this.timezone = slot.timezone();
        this.startDateStatus = require(slot.startDateStatus(), "startDateStatus");
        this.startTimeStatus = require(slot.startTimeStatus(), "startTimeStatus");
        this.endDateStatus = require(slot.endDateStatus(), "endDateStatus");
        this.endTimeStatus = require(slot.endTimeStatus(), "endTimeStatus");
        this.endApproximate = slot.endApproximate();
    }

    private static <T> T require(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value;
    }
}
