package nl.loc.data.event;

/** Categories stored in catalog.category. Source labels are not values of this type. */
public enum Category {
    MUSIC_AND_NIGHTLIFE("Music & Nightlife"),
    ARTS_AND_CULTURE("Arts & Culture"),
    SPORTS("Sports"),
    BUSINESS_AND_CAREERS("Business & Careers"),
    TECHNOLOGY_AND_SCIENCE("Technology & Science"),
    LEARNING_AND_SKILLS("Learning & Skills"),
    NATURE_AND_SUSTAINABILITY("Nature & Sustainability"),
    OTHER("Other");

    private final String catalogName;

    Category(String catalogName) {
        this.catalogName = catalogName;
    }

    public String catalogName() {
        return catalogName;
    }
}
