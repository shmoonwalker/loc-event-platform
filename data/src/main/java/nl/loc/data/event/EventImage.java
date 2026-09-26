package nl.loc.data.event;

/** List order is retained as display order when published. */
public record EventImage(String url, Integer width, Integer height, String ratio,
                         String attribution, Boolean fallback) {
}
