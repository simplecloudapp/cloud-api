package app.simplecloud.api.internal.integration.player;

import java.util.Optional;
import java.util.function.Function;

public final class ConnectTarget {

    private final String serverName;
    private final String serverId;

    private ConnectTarget(String serverName, String serverId) {
        this.serverName = serverName;
        this.serverId = serverId;
    }

    public static ConnectTarget byName(String serverName) {
        return new ConnectTarget(serverName, null);
    }

    public static ConnectTarget byId(String serverId) {
        return new ConnectTarget(null, serverId);
    }

    public <T> Optional<T> resolve(Function<String, Optional<T>> registeredServerByName) {
        if (serverId != null) {
            return ServerNameRegistry.nameOf(serverId).flatMap(registeredServerByName);
        }
        return registeredServerByName.apply(serverName);
    }

    @Override
    public String toString() {
        return serverId != null ? "server id " + serverId : "server " + serverName;
    }
}
