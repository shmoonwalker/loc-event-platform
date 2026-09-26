package nl.loc.data.source.rvo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import nl.loc.data.event.Category;
import nl.loc.data.event.CategoryAssignment;

/** Maps RVO subjects. A session, webinar, course, or vragenuur also adds Learning & Skills. */
final class RvoCategoryMapper {
    private static final Map<String, Category> SUBJECTS = Map.of(
            "internationaal ondernemen", Category.BUSINESS_AND_CAREERS,
            "bouwen en wonen", Category.BUSINESS_AND_CAREERS,
            "ontwikkelingssamenwerking", Category.BUSINESS_AND_CAREERS,
            "innovatie, onderzoek en onderwijs", Category.TECHNOLOGY_AND_SCIENCE,
            "klimaat en energie", Category.NATURE_AND_SUSTAINABILITY,
            "dieren en natuur", Category.NATURE_AND_SUSTAINABILITY,
            "landbouw", Category.NATURE_AND_SUSTAINABILITY
    );

    private static final List<String> LEARNING_SIGNALS = List.of(
            "sessie",
            "session",
            "webinar",
            "cursus",
            "course",
            "vragenuur"
    );

    private RvoCategoryMapper() {
    }

    static List<Category> map(JsonNode root) {
        List<Category> matched = new ArrayList<>();
        for (String subject : texts(root.path("subjects"))) {
            Category category = SUBJECTS.get(normalize(subject));
            if (category != null) {
                matched.add(category);
            }
        }
        if (signalsLearning(root)) {
            matched.add(Category.LEARNING_AND_SKILLS);
        }
        return CategoryAssignment.resolve(matched);
    }

    private static boolean signalsLearning(JsonNode root) {
        if (containsLearningSignal(text(root, "title"))) {
            return true;
        }
        for (String tag : texts(root.path("tags"))) {
            if (containsLearningSignal(tag)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsLearningSignal(String value) {
        if (value == null) {
            return false;
        }
        String normalized = normalize(value);
        for (String signal : LEARNING_SIGNALS) {
            if (normalized.contains(signal)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> texts(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            if (item.isTextual() && !item.asText().isBlank()) {
                values.add(item.asText());
            }
        }
        return values;
    }

    private static String text(JsonNode parent, String field) {
        JsonNode node = parent.path(field);
        if (!node.isTextual() || node.asText().isBlank()) {
            return null;
        }
        return node.asText();
    }

    private static String normalize(String value) {
        return value.strip().toLowerCase(Locale.ROOT);
    }
}
