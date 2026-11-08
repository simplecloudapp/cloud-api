package app.simplecloud.api.internal.group;

import org.jetbrains.annotations.Nullable;

public record GroupResources(
        @Nullable Integer minMemory,
        @Nullable Integer maxMemory,
        @Nullable Integer maxPlayers,
        @Nullable Boolean active,
        @Nullable Integer priority) {

    public static final GroupResources NONE = new GroupResources(null, null, null, null, null);
}
