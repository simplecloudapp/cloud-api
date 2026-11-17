package app.simplecloud.api.internal.networkstate.read;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * The part of an ordered list that {@code limit} and {@code offset} select, by the controller's rules
 * ({@code parseOptionalListPagination}): without a limit the whole list, a limit from 1 to 500, an offset of
 * 0 or more, and an offset only together with a limit.
 *
 * @param limit the largest number of items, or null for all of them
 */
record Page(@Nullable Integer limit, int offset) {

    private static final int LARGEST_LIMIT = 500;
    private static final Page WHOLE_LIST = new Page(null, 0);

    /**
     * Returns the page, or empty if the controller answers these values with an error.
     */
    static Optional<Page> parse(@Nullable Integer limit, @Nullable Integer offset) {
        if (limit == null) {
            return offset == null ? Optional.of(WHOLE_LIST) : Optional.empty();
        }
        boolean limitInRange = limit >= 1 && limit <= LARGEST_LIMIT;
        int effectiveOffset = offset == null ? 0 : offset;
        if (!limitInRange || effectiveOffset < 0) {
            return Optional.empty();
        }
        return Optional.of(new Page(limit, effectiveOffset));
    }

    <T> List<T> select(List<T> ordered) {
        int start = Math.min(offset, ordered.size());
        int end = limit == null ? ordered.size() : Math.min(ordered.size(), start + limit);
        return ordered.subList(start, end);
    }
}
