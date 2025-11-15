package app.simplecloud.api.internal.server;

import app.simplecloud.api.blueprint.Blueprint;
import app.simplecloud.api.group.Group;
import app.simplecloud.api.internal.group.GroupImpl;
import app.simplecloud.api.server.Server;
import app.simplecloud.api.server.ServerState;
import app.simplecloud.api.web.models.ModelsServerSummary;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.Map;

public class ServerImpl implements Server {
    private final ModelsServerSummary delegate;
    private Blueprint blueprint;
    private Group serverGroup;

    public ServerImpl(ModelsServerSummary delegate) {
        this.delegate = delegate;
    }

    @Override
    public String getServerId() {
        String serverId = delegate.getServerId();
        if (serverId == null) {
            throw new IllegalStateException("Server ID is null");
        }
        return serverId;
    }

    @Override
    public String getPersistentServerId() {
        String persistentServerId = delegate.getPersistentServerId();
        if (persistentServerId == null) {
            throw new IllegalStateException("Persistent server ID is null");
        }
        return persistentServerId;
    }

    @Override
    public int getNumericalId() {
        Integer numericalId = delegate.getNumericalId();
        if (numericalId == null) {
            throw new IllegalStateException("Numerical ID is null");
        }
        return numericalId;
    }

    @Override
    public String getServerGroupId() {
        String serverGroupId = delegate.getServerGroupId();
        if (serverGroupId == null) {
            throw new IllegalStateException("Server group ID is null");
        }
        return serverGroupId;
    }

    @Override
    public String getServerhostId() {
        String serverhostId = delegate.getServerhostId();
        if (serverhostId == null) {
            throw new IllegalStateException("Serverhost ID is null");
        }
        return serverhostId;
    }

    @Override
    public String getNetworkId() {
        String networkId = delegate.getNetworkId();
        if (networkId == null) {
            throw new IllegalStateException("Network ID is null");
        }
        return networkId;
    }

    @Override
    @Nullable
    public String getIp() {
        return delegate.getIp();
    }

    @Override
    @Nullable
    public Integer getPort() {
        return delegate.getPort();
    }

    @Override
    public Integer getMinMemory() {
        Integer minMemory = delegate.getMinMemory();
        if (minMemory == null) {
            throw new IllegalStateException("Min memory is null");
        }
        return minMemory;
    }

    @Override
    public Integer getMaxMemory() {
        Integer maxMemory = delegate.getMaxMemory();
        if (maxMemory == null) {
            throw new IllegalStateException("Max memory is null");
        }
        return maxMemory;
    }

    @Override
    @Nullable
    public Double getCpuUsage() {
        BigDecimal cpuUsage = delegate.getCpuUsage();
        return cpuUsage != null ? cpuUsage.doubleValue() : null;
    }

    @Override
    @Nullable
    public Double getMemoryUsage() {
        BigDecimal memoryUsage = delegate.getMemoryUsage();
        return memoryUsage != null ? memoryUsage.doubleValue() : null;
    }

    @Override
    @Nullable
    public Integer getPlayerCount() {
        return delegate.getPlayerCount();
    }

    @Override
    public Integer getMaxPlayers() {
        Integer maxPlayers = delegate.getMaxPlayers();
        if (maxPlayers == null) {
            throw new IllegalStateException("Max players is null");
        }
        return maxPlayers;
    }

    @Override
    public ServerState getState() {
        String stateStr = delegate.getState();
        if (stateStr == null) {
            throw new IllegalStateException("Server state is null");
        }
        try {
            return ServerState.valueOf(stateStr);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Invalid server state: " + stateStr, e);
        }
    }

    @Override
    public String getCreatedAt() {
        String createdAt = delegate.getCreatedAt();
        if (createdAt == null) {
            throw new IllegalStateException("Created at timestamp is null");
        }
        return createdAt;
    }

    @Override
    public String getUpdatedAt() {
        String updatedAt = delegate.getUpdatedAt();
        if (updatedAt == null) {
            throw new IllegalStateException("Updated at timestamp is null");
        }
        return updatedAt;
    }

    @Override
    @Nullable
    public String getLastActivity() {
        return delegate.getLastActivity();
    }

    @Override
    @Nullable
    public Map<String, Object> getProperties() {
        return delegate.getProperties();
    }

    @Override
    @Nullable
    public Blueprint getBlueprint() {
        return null;
    }

    @Override
    public Group getServerGroup() {
        if (serverGroup == null) {
            if (delegate.getServerGroup() == null) {
                throw new IllegalStateException("Server group is null");
            }
            serverGroup = new GroupImpl(delegate.getServerGroup());
        }
        return serverGroup;
    }
}

