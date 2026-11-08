package app.simplecloud.api.player;

import java.util.List;

/**
 * One page of a paginated list.
 *
 * @param items the items on this page
 * @param total the number of items across all pages
 * @param <T> the item type
 */
public record Page<T>(List<T> items, long total) {

    public Page {
        items = List.copyOf(items);
    }

    /**
     * Returns whether more items exist after this page.
     *
     * @param offset the offset this page was requested with
     * @return true if {@code offset + items().size()} is below the total
     */
    public boolean hasMore(int offset) {
        return offset + items.size() < total;
    }
}
