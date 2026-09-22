package nl.loc.data.event;

public record EventLocation(
        LocationType locationType,
        String venueName,
        String address,
        String city,
        String postalCode,
        String country,
        Double latitude,
        Double longitude
) {
}
