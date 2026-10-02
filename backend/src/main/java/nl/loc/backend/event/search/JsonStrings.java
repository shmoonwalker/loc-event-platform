package nl.loc.backend.event.search;

import java.util.ArrayList;
import java.util.List;

/** Reads a JSON array of strings from a published snapshot field. */
final class JsonStrings {

    private JsonStrings() {
    }

    static List<String> array(String json) {
        if (json == null) {
            return List.of();
        }
        String body = json.trim();
        if (body.length() < 2 || body.charAt(0) != '[') {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        int index = 1;
        while (index < body.length()) {
            while (index < body.length() && (body.charAt(index) == ' ' || body.charAt(index) == ',')) {
                index++;
            }
            if (index >= body.length() || body.charAt(index) == ']') {
                break;
            }
            if (body.charAt(index) != '"') {
                break;
            }
            index++;
            StringBuilder value = new StringBuilder();
            while (index < body.length()) {
                char current = body.charAt(index);
                if (current == '\\' && index + 1 < body.length()) {
                    value.append(body.charAt(index + 1));
                    index += 2;
                    continue;
                }
                if (current == '"') {
                    index++;
                    break;
                }
                value.append(current);
                index++;
            }
            values.add(value.toString());
        }
        return List.copyOf(values);
    }
}
