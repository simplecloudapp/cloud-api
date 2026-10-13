package app.simplecloud.api.internal.integration.player;

import java.util.Optional;
import java.util.function.Function;

public final class ServerNameRegistry {

    private static final Function<String, Optional<String>> NONE = serverId -> Optional.empty();

    private static volatile Function<String, Optional<String>> mapping = NONE;

    private ServerNameRegistry() {
    }

    public static void register(Function<String, Optional<String>> serverIdToName) {
        mapping = serverIdToName;
    }

    public static void unregister(Function<String, Optional<String>> serverIdToName) {
        if (mapping == serverIdToName) {
            mapping = NONE;
        }
    }

    public static Optional<String> nameOf(String serverId) {
        Optional<String> name = mapping.apply(serverId);
        return name != null ? name : Optional.empty();
    }
}
