package nl.loc.data.event;

import java.time.Instant;

public record EventTimeSlot(Instant startsAt, Instant endsAt) {}