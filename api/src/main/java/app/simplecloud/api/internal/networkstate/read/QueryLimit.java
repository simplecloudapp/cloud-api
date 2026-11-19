package app.simplecloud.api.internal.networkstate.read;

import app.simplecloud.api.CloudApiException;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The limit of a group or persistent server query.
 */
final class QueryLimit {

    private QueryLimit() {
    }

    /**
     * Returns the first {@code limit} items, or all of them without a limit.
     *
     * @throws CloudApiException with {@link CloudApiException.Reason#INVALID_REQUEST} for a limit below 1
     */
    static <T> List<T> firstOf(List<T> items, @Nullable Integer limit) {
        if (limit == null) {
            return items;
        }
        if (limit < 1) {
            throw new CloudApiException(CloudApiException.Reason.INVALID_REQUEST,
                    "The limit must be at least 1, not " + limit);
        }
        int end = Math.min(limit, items.size());
        return items.subList(0, end);
    }
}
