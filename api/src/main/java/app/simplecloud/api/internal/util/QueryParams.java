package app.simplecloud.api.internal.util;

import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.stream.Collectors;

public final class QueryParams {

    private QueryParams() {}

    @Nullable
    public static String joinOrNull(@Nullable Collection<?> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.stream()
                .map(value -> value instanceof Enum<?> e ? e.name() : String.valueOf(value))
                .collect(Collectors.joining(","));
    }
}
