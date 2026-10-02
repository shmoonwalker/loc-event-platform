package nl.loc.backend.city.model;

import java.util.Locale;

public final class CityNames {

    private CityNames() {
    }

    public static String fromSlug(String slug) {
        String[] parts = slug.split("-");
        StringBuilder name = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                name.append(' ');
            }
            String part = parts[i];
            if (part.isEmpty()) {
                continue;
            }
            name.append(part.substring(0, 1).toUpperCase(Locale.ROOT));
            if (part.length() > 1) {
                name.append(part.substring(1));
            }
        }
        return name.toString();
    }
}
