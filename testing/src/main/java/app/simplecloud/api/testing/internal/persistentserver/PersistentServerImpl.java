package app.simplecloud.api.testing.internal.persistentserver;

import app.simplecloud.api.group.GroupServerType;
import app.simplecloud.api.group.SourceConfig;
import app.simplecloud.api.group.WorkflowsConfig;
import app.simplecloud.api.persistentserver.PersistentServer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

public class PersistentServerImpl implements PersistentServer {

    protected final PersistentServerData data;

    public PersistentServerImpl(PersistentServerData data) {
        this.data = data;
    }

    @Override
    public String getPersistentServerId() {
        return data.id;
    }

    @Override
    public String getName() {
        return data.name;
    }

    @Override
    public @Nullable Boolean isActive() {
        return data.active;
    }

    @Override
    public @Nullable Integer getPriority() {
        return data.priority;
    }

    @Override
    public int getPlayerCount() {
        return data.playerCount();
    }

    @Override
    public @Nullable String getServerhostId() {
        return data.serverhostId;
    }

    @Override
    public @Nullable Integer getMinMemory() {
        return data.minMemory;
    }

    @Override
    public @Nullable Integer getMaxMemory() {
        return data.maxMemory;
    }

    @Override
    public @Nullable Integer getMaxPlayers() {
        return data.maxPlayers;
    }

    @Override
    public @Nullable SourceConfig getSource() {
        return data.source;
    }

    @Override
    public @Nullable WorkflowsConfig getWorkflows() {
        return data.workflows;
    }

    @Override
    public Map<String, Object> getProperties() {
        return new LinkedHashMap<>(data.properties);
    }

    @Override
    public List<String> getTags() {
        return List.copyOf(data.tags);
    }

    @Override
    public GroupServerType getType() {
        return data.type;
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
    public String toString() {
        return "PersistentServer[" + data.name + ", id=" + data.id + "]";
    }
}
