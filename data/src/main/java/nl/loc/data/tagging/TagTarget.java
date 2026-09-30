package nl.loc.data.tagging;

import java.util.List;

import nl.loc.data.text.HtmlPlainText;

public record TagTarget(long eventId, String title, String description, List<String> categoryNames, int attempts) {

    /** Shorter descriptions count as missing: the event uses local tags and is never sent to Gemini. */
    public static boolean needsGemini(String description) {
        String cleaned = HtmlPlainText.convert(description);
        return cleaned != null && cleaned.length() >= TagPrompt.MIN_DESCRIPTION_LENGTH;
    }

    public boolean geminiEligible() {
        return needsGemini(description);
    }

    public String descriptionForPrompt() {
        String text = HtmlPlainText.convert(description);
        if (text == null) text = "";
        if (text.length() <= TagPrompt.MAX_DESCRIPTION_LENGTH) {
            return text;
        }
        return text.substring(0, TagPrompt.MAX_DESCRIPTION_LENGTH);
    }
}
