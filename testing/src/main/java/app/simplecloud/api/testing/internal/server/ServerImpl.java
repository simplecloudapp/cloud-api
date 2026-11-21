package app.simplecloud.api.testing.internal.server;

import app.simplecloud.api.base.ServerBase;
import app.simplecloud.api.blueprint.Blueprint;
import app.simplecloud.api.group.Group;
import app.simplecloud.api.persistentserver.PersistentServer;
import app.simplecloud.api.server.Server;
import app.simplecloud.api.server.ServerState;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

public class ServerImpl implements Server {

    protected final ServerData data;
    private final Group group;

    public ServerImpl(ServerData data, Group group) {
        this.data = data;
        this.group = group;
    }

    public String getName() {
        return group.getName() + "-" + data.numericalId;
    }

    @Override
    public String getServerId() {
        return data.id;
    }

    @Override
    public @Nullable String getPersistentServerId() {
        return null;
    }

    @Override
    public int getNumericalId() {
        return data.numericalId;
    }

    @Override
    public String getServerGroupId() {
        return group.getServerGroupId();
    }

    @Override
    public String getServerhostId() {
        return data.serverhostId;
    }

    @Override
    public String getNetworkId() {
        return data.networkId;
    }

    @Override
    public @Nullable String getIp() {
        return data.ip;
    }

    @Override
    public @Nullable Integer getPort() {
        return data.port;
    }

    @Override
    public Integer getMinMemory() {
        return data.minMemory;
    }

    @Override
    public Integer getMaxMemory() {
        return data.maxMemory;
    }

    @Override
    public @Nullable Double getCpuUsage() {
        return null;
    }

    @Override
    public @Nullable Double getMemoryUsage() {
        return null;
    }

    @Override
    public Integer getPlayerCount() {
        return data.playerCount;
    }

    @Override
    public Integer getMaxPlayers() {
        return data.maxPlayers;
    }

    @Override
    public ServerState getState() {
        return data.state;
    }

    @Override
    public String getCreatedAt() {
        return data.createdAt;
    }

    @Override
    public String getUpdatedAt() {
        return data.updatedAt;
    }

    @Override
    public @Nullable String getLastActivity() {
        return data.updatedAt;
    }

    @Override
    public Map<String, Object> getProperties() {
        return new LinkedHashMap<>(data.properties);
    }

    @Override
    public @Nullable Blueprint getBlueprint() {
        return null;
    }

    @Override
    public ServerBase getServerBase() {
        return group;
    }

    @Override
    public Group getGroup() {
        return group;
    }

    @Override
    public @Nullable PersistentServer getPersistentServer() {
        return null;
    }

    @Override
    public String toString() {
        return "Server[" + getName() + ", id=" + data.id + ", state=" + data.state + "]";
    }
}
