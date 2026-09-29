package nl.loc.data.tagging;

final class TagPrompt {

    static final int VERSION = 1;
    static final int MIN_DESCRIPTION_LENGTH = 80;
    static final int MAX_DESCRIPTION_LENGTH = 800;
    static final int MAX_GEMINI_TAGS = 5;

    private TagPrompt() {
    }

    static String text(String categories, String title, String description) {
        return """
                You assign discovery tags to one event for an English event website.

                Use ONLY these slugs:
                %s

                The list is grouped as format, audience, setting, and topic. Mix groups when the text supports it. Do not take a tag from a group unless the text clearly supports it.

                Rules:
                - Return 3 to 5 slugs you can justify from the title, description, and Loc categories.
                - If you cannot justify 3, return fewer. Never invent a lineup, age limit, price, indoor/outdoor, time of day, or genre.
                - When the text names the type of event (concert, workshop, conference, meetup), include that format tag if it is on the list.
                - When the text says workshop, sessie, basissessie, voorlichtingssessie, training, or conference, pick that format slug (workshop or conference).
                - Dutch or English text is fine. Output slugs in English only.
                - Do not use a Loc category name as a tag (for example do not output "sports" because the category is Sports).
                - Do not add tags that are not in the list.
                - If the description is empty, use only what the title and categories clearly support. If that is not enough, return fewer tags or an empty list.

                Return JSON only, no markdown:
                {"tags":["slug"]}

                Loc categories: %s
                Title: %s
                Description: %s
                """.formatted(ContentTag.geminiSlugList(), blank(categories), blank(title), blank(description));
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? "" : value.strip();
    }
}
