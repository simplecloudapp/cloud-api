package app.simplecloud.api.internal.server;

import app.simplecloud.api.blueprint.Blueprint;
import app.simplecloud.api.group.Group;
import app.simplecloud.api.internal.group.GroupImpl;
import app.simplecloud.api.internal.persistentserver.PersistentServerImpl;
import app.simplecloud.api.persistentserver.PersistentServer;
import app.simplecloud.api.server.Server;
import app.simplecloud.api.server.ServerState;
import app.simplecloud.api.base.ServerBase;
import app.simplecloud.api.web.models.ModelsServerSummary;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.Map;

public class ServerImpl implements Server {
    private final ModelsServerSummary delegate;
    private Blueprint blueprint;
    private Group group;
    private PersistentServer persistentServer;

    public ServerImpl(ModelsServerSummary delegate) {
        this.delegate = delegate;
    }

    public void setPersistentServer(PersistentServer persistentServer) {
        this.persistentServer = persistentServer;
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
    @Nullable
    public String getPersistentServerId() {
        return delegate.getPersistentServerId();
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
    @Nullable
    public String getServerGroupId() {
        return delegate.getServerGroupId();
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
        ModelsServerSummary.StateEnum stateStr = delegate.getState();
        if (stateStr == null) {
            throw new IllegalStateException("Server state is null");
        }
        try {
            return ServerState.parse(stateStr);
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
    public ServerBase getServerBase() {
        if (group != null) {
            return group;
        }
        if (persistentServer != null) {
            return persistentServer;
        }
        Group g = getGroup();
        if (g != null) {
            return g;
        }
        PersistentServer ps = getPersistentServer();
        if (ps != null) {
            return ps;
        }
        throw new IllegalStateException("Server has no base configuration (neither group nor persistent server)");
    }

    @Override
    @Nullable
    public Group getGroup() {
        if (group == null && delegate.getServerGroup() != null) {
            group = new GroupImpl(delegate.getServerGroup());
        }
        return group;
    }

    @Override
    @Nullable
    public PersistentServer getPersistentServer() {
        if  (persistentServer == null && delegate.getPersistentServer() != null) {
            persistentServer = new PersistentServerImpl(delegate.getPersistentServer());
        }

        return persistentServer;
    }
}
