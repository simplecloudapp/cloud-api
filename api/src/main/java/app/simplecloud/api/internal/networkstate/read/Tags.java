package app.simplecloud.api.internal.networkstate.read;

import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

/**
 * The tag filter of the controller's lists.
 */
final class Tags {

    private Tags() {
    }

    /**
     * Tags match as Postgres's {@code &&} does: one tag in common is enough. An item without tags matches no
     * tag filter.
     */
    static boolean haveOneInCommon(Collection<String> wanted, @Nullable Collection<String> tagsOfItem) {
        return tagsOfItem != null && !Collections.disjoint(wanted, tagsOfItem);
    }
}
