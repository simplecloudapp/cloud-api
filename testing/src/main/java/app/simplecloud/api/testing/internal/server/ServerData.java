package app.simplecloud.api.testing.internal.server;

import app.simplecloud.api.server.Server;
import app.simplecloud.api.server.ServerState;
import app.simplecloud.api.testing.internal.group.GroupData;
import app.simplecloud.api.testing.internal.group.GroupImpl;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ServerData {

    public final String id;
    public final String networkId;
    public final GroupData group;
    public final int numericalId;
    public final String createdAt;
    public String updatedAt;
    public String serverhostId = "";
    public @Nullable String ip;
    public @Nullable Integer port;
    public int minMemory;
    public int maxMemory;
    public int maxPlayers;
    public int playerCount;
    public ServerState state = ServerState.UNKNOWN_STATE;
    public final Map<String, Object> properties = new LinkedHashMap<>();
    public boolean running = true;
    public @Nullable TestServerImpl handle;

    public ServerData(String id, String networkId, GroupData group, int numericalId, String createdAt) {
        this.id = id;
        this.networkId = networkId;
        this.group = group;
        this.numericalId = numericalId;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public String name() {
        return group.name + "-" + numericalId;
    }

    public ServerData copy() {
        ServerData copy = new ServerData(id, networkId, group, numericalId, createdAt);
        copy.updatedAt = updatedAt;
        copy.serverhostId = serverhostId;
        copy.ip = ip;
        copy.port = port;
        copy.minMemory = minMemory;
        copy.maxMemory = maxMemory;
        copy.maxPlayers = maxPlayers;
        copy.playerCount = playerCount;
        copy.state = state;
        copy.properties.putAll(properties);
        copy.running = running;
        return copy;
    }

    public Server snapshot() {
        return new ServerImpl(copy(), new GroupImpl(group.copy()));
    }
}
