package nl.loc.data.tagging;

import java.util.List;

public record TagTarget(long eventId, String title, String description, List<String> categoryNames, int attempts) {

    public boolean geminiEligible() {
        return description != null && description.strip().length() >= TagPrompt.MIN_DESCRIPTION_LENGTH;
    }

    public String descriptionForPrompt() {
        String text = description == null ? "" : description.strip();
        if (text.length() <= TagPrompt.MAX_DESCRIPTION_LENGTH) {
            return text;
        }
        return text.substring(0, TagPrompt.MAX_DESCRIPTION_LENGTH);
    }
}
