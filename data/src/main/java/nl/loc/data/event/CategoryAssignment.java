package nl.loc.data.event;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;

/** Other is used only when no real category matched. */
public final class CategoryAssignment {
    private CategoryAssignment() {
    }

    public static List<Category> resolve(Collection<Category> matched) {
        EnumSet<Category> categories = EnumSet.noneOf(Category.class);
        if (matched != null) {
            for (Category category : matched) {
                if (category != null && category != Category.OTHER) {
                    categories.add(category);
                }
            }
        }
        if (categories.isEmpty()) {
            return List.of(Category.OTHER);
        }
        return List.copyOf(categories);
    }
}
