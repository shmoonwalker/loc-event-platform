package nl.loc.backend.user.model;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/** Preset avatars; the images ship with the frontend under /avatars/. */
public enum AvatarPreset {
    SUN,
    MOON,
    LEAF,
    WAVE,
    MOUNTAIN,
    STAR,
    FLOWER,
    CLOUD;

    public static AvatarPreset random() {
        AvatarPreset[] presets = values();
        return presets[ThreadLocalRandom.current().nextInt(presets.length)];
    }

    public String url() {
        return "/avatars/" + name().toLowerCase(Locale.ROOT) + ".svg";
    }
}
