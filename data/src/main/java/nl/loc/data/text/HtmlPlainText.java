package nl.loc.data.text;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

/** Converts source HTML into plain text with paragraph breaks and list markers. */
public final class HtmlPlainText {

    private HtmlPlainText() {
    }

    public static String convert(String html) {
        if (html == null || html.isBlank()) {
            return null;
        }
        Element body = Jsoup.parseBodyFragment(html).body();
        body.select("script, style, template").remove();
        StringBuilder output = new StringBuilder();
        append(body, output);
        String text = output.toString()
                .replaceAll(" *\n *", "\n")
                .replaceAll("\n{3,}", "\n\n")
                .strip();
        return text.isEmpty() ? null : text;
    }

    private static void append(Node node, StringBuilder output) {
        if (node instanceof TextNode text) {
            // Collapse source formatting whitespace, after the parser decodes entities.
            output.append(text.getWholeText().replaceAll("[\\s\\u00a0]+", " "));
            return;
        }
        if (!(node instanceof Element element)) {
            return;
        }
        String tag = element.normalName();
        if (tag.equals("br")) {
            output.append('\n');
            return;
        }
        boolean listItem = tag.equals("li");
        if (listItem) {
            output.append("\n- ");
        } else if (element.isBlock()) {
            output.append("\n\n");
        }
        for (Node child : element.childNodes()) {
            append(child, output);
        }
        if (listItem) {
            output.append('\n');
        } else if (element.isBlock()) {
            output.append("\n\n");
        }
    }
}
