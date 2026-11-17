package nl.loc.backend.photo.model;

public enum PhotoVariant {
    CARD("card", "card.jpg", 640),
    FULL("full", "full.jpg", 1600);

    private final String param;
    private final String fileName;
    private final int maxSide;

    PhotoVariant(String param, String fileName, int maxSide) {
        this.param = param;
        this.fileName = fileName;
        this.maxSide = maxSide;
    }

    public String param() {
        return param;
    }

    public String fileName() {
        return fileName;
    }

    public int maxSide() {
        return maxSide;
    }

    public static PhotoVariant fromParam(String value) {
        for (PhotoVariant variant : values()) {
            if (variant.param.equals(value)) {
                return variant;
            }
        }
        return null;
    }
}
