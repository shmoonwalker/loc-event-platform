package nl.loc.data.catalog;

import jakarta.persistence.*;
import nl.loc.data.event.LocationType;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;


@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "event_location",
        schema = "catalog")
public class CatalogEventLocation {

    @Id
    private Long id;

    @OneToOne
    @MapsId
    @JoinColumn(name = "event_id")
    private CatalogEvent event;

    @Enumerated(EnumType.STRING)
    @Column(name = "location_type", nullable = false)
    private LocationType locationType;

    @Column
    private String venueName;

    @Column
    private String address;

    @Column
    private String city;

    @Column
    private String postalCode;

    @Column
    private String country;

    @Column
    private Double latitude;

    @Column
    private Double longitude;

    @Column(name = "external_venue_id")
    private String externalVenueId;

    @Column(name = "country_code")
    private String countryCode;

    public CatalogEventLocation(
            CatalogEvent event,
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
        this.event = require(event, "event");
        updateFromImport(
                locationType,
                venueName,
                address,
                city,
                postalCode,
                country,
                latitude,
                longitude,
                externalVenueId,
                countryCode
        );
    }

    public void updateFromImport(
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
    )
{
        this.locationType = require(locationType, "locationType");
        this.venueName = normalize(venueName);
        this.address = normalize(address);
        this.city = normalize(city);
        this.postalCode = normalize(postalCode);
        this.country = normalize(country);
        this.latitude = latitude;
        this.longitude = longitude;
        this.externalVenueId = normalize(externalVenueId);
        this.countryCode = normalize(countryCode);
    }

    private static <T> T require(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
