package app.simplecloud.api.internal.integration.player;

import build.buf.gen.simplecloud.player.v2.ConnectPlayerRequest;

import java.util.Optional;

public final class ConnectTarget {

    private final String serverName;
    private final String serverId;

    private ConnectTarget(String serverName, String serverId) {
        this.serverName = serverName;
        this.serverId = serverId;
    }

    /**
     * Reads the target of a connect request. If both a server ID and a server name are set, the ID wins.
     */
    public static ConnectTarget from(ConnectPlayerRequest request) {
        if (request.getServerId().isEmpty()) {
            return new ConnectTarget(request.getServerName(), null);
        }
        return new ConnectTarget(null, request.getServerId());
    }

    /**
     * Resolves the target to a server registered on this proxy.
     */
    public <T> Optional<T> resolve(RegisteredServers<T> registeredServers) {
        if (serverId == null) {
            return registeredServers.findByName(serverName);
        }
        return registeredServers.findById(serverId);
    }

    @Override
    public String toString() {
        return serverId != null ? "server id " + serverId : "server " + serverName;
    }
}
