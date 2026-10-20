package app.simplecloud.api.internal.integration.player;

import app.simplecloud.api.internal.web.Identifiers;
import app.simplecloud.api.server.Server;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;

public final class ConnectTarget {

    private final String serverName;
    private final String serverId;
    private final Function<String, CompletableFuture<Optional<Server>>> serverLookup;

    private ConnectTarget(String serverName, String serverId,
                          Function<String, CompletableFuture<Optional<Server>>> serverLookup) {
        this.serverName = serverName;
        this.serverId = serverId;
        this.serverLookup = serverLookup;
    }

    public static ConnectTarget byName(String serverName, Function<String, CompletableFuture<Optional<Server>>> serverLookup) {
        return new ConnectTarget(serverName, null, serverLookup);
    }

    public static ConnectTarget byId(String serverId, Function<String, CompletableFuture<Optional<Server>>> serverLookup) {
        return new ConnectTarget(null, serverId, serverLookup);
    }

    /**
     * Resolves the target to a server registered on this proxy.
     *
     * <p>A server ID is resolved through {@link ServerNameRegistry} first. Proxies may register servers
     * under any name, so otherwise the server is looked up and matched by its address.
     */
    public <T> CompletableFuture<Optional<T>> resolve(
            Function<String, Optional<T>> registeredServerByName,
            BiFunction<String, Integer, Optional<T>> registeredServerByAddress
    ) {
        if (serverId != null) {
            return resolveById(serverId, registeredServerByName, registeredServerByAddress);
        }
        Optional<T> registered = registeredServerByName.apply(serverName);
        if (registered.isPresent() || !Identifiers.isUuid(serverName)) {
            return CompletableFuture.completedFuture(registered);
        }
        // Older controllers send the server ID in the name field.
        return resolveById(serverName, registeredServerByName, registeredServerByAddress);
    }

    private <T> CompletableFuture<Optional<T>> resolveById(
            String id,
            Function<String, Optional<T>> registeredServerByName,
            BiFunction<String, Integer, Optional<T>> registeredServerByAddress
    ) {
        Optional<T> registered = ServerNameRegistry.nameOf(id).flatMap(registeredServerByName);
        if (registered.isPresent()) {
            return CompletableFuture.completedFuture(registered);
        }
        return serverLookup.apply(id).thenApply(server -> server
                .filter(found -> found.getIp() != null && found.getPort() != null)
                .flatMap(found -> registeredServerByAddress.apply(found.getIp(), found.getPort())));
    }

    public static boolean hasAddress(SocketAddress address, String ip, int port) {
        if (!(address instanceof InetSocketAddress socketAddress) || socketAddress.getPort() != port) {
            return false;
        }
        if (ip.equalsIgnoreCase(socketAddress.getHostString())) {
            return true;
        }
        return socketAddress.getAddress() != null && ip.equals(socketAddress.getAddress().getHostAddress());
    }

    @Override
    public String toString() {
        return serverId != null ? "server id " + serverId : "server " + serverName;
    }
}
