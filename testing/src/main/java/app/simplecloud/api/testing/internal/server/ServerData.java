package app.simplecloud.api.testing.internal.server;

import app.simplecloud.api.server.Server;
import app.simplecloud.api.server.ServerState;
import app.simplecloud.api.testing.internal.blueprint.BlueprintData;
import app.simplecloud.api.testing.internal.group.GroupData;
import app.simplecloud.api.testing.internal.group.GroupImpl;
import app.simplecloud.api.testing.internal.persistentserver.PersistentServerData;
import app.simplecloud.api.testing.internal.persistentserver.PersistentServerImpl;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

public final class ServerData {

    public final String id;
    public final String networkId;
    public final @Nullable GroupData group;
    public final @Nullable PersistentServerData persistent;
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
    public @Nullable BlueprintData blueprint;
    public boolean running = true;
    public @Nullable TestServerImpl handle;

    public ServerData(
            String id,
            String networkId,
            @Nullable GroupData group,
            @Nullable PersistentServerData persistent,
            int numericalId,
            String createdAt) {
        if ((group == null) == (persistent == null)) {
            throw new IllegalArgumentException("A server belongs to exactly one group or persistent server");
        }
        this.id = id;
        this.networkId = networkId;
        this.group = group;
        this.persistent = persistent;
        this.numericalId = numericalId;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public String baseName() {
        return group != null ? group.name : persistent.name;
    }

    public String name() {
        return group != null ? group.name + "-" + numericalId : persistent.name;
    }

    public ServerData copy() {
        ServerData copy = new ServerData(id, networkId, group, persistent, numericalId, createdAt);
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
        copy.blueprint = blueprint == null ? null : blueprint.copy();
        return copy;
    }

    public Server snapshot() {
        return new ServerImpl(
                copy(),
                group == null ? null : new GroupImpl(group.copy()),
                persistent == null ? null : new PersistentServerImpl(persistent.copy()));
    }
}
