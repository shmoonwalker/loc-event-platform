package nl.loc.data.event;

/** Source organizer identity plus display fields. Loc UUIDs are minted at publication. */
public record EventOrganizer(String externalId, String name, String description, String site) {
}
