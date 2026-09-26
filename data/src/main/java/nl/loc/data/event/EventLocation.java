package nl.loc.data.event;

public record EventLocation(
        LocationType locationType,
        String venueName,
        String address,
        String city,
        String postalCode,
        String country,
        Double latitude,
        Double longitude,
        String externalVenueId,
        String countryCode
) {
    public EventLocation(LocationType locationType, String venueName, String address, String city,
                         String postalCode, String country, Double latitude, Double longitude) {
        this(locationType, venueName, address, city, postalCode, country, latitude, longitude, null, null);
    }
}
