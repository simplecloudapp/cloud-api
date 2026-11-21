package app.simplecloud.api.testing.internal.persistentserver;

import app.simplecloud.api.group.GroupServerType;
import app.simplecloud.api.group.SourceConfig;
import app.simplecloud.api.group.WorkflowsConfig;
import app.simplecloud.api.persistentserver.PersistentServer;
import app.simplecloud.api.testing.internal.server.ServerData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PersistentServerData {

    public final String id;
    public final String createdAt;
    public String name;
    public String updatedAt;
    public GroupServerType type = GroupServerType.SERVER;
    public @Nullable Integer minMemory;
    public @Nullable Integer maxMemory;
    public @Nullable Integer maxPlayers;
    public @Nullable Boolean active = Boolean.TRUE;
    public @Nullable Integer priority;
    public @Nullable String serverhostId;
    public @Nullable SourceConfig source;
    public @Nullable WorkflowsConfig workflows;
    public final Map<String, Object> properties = new LinkedHashMap<>();
    public final List<String> tags = new ArrayList<>();
    public @Nullable ServerData server;
    public boolean deleted;
    public @Nullable TestPersistentServerImpl handle;

    public PersistentServerData(String id, String name, String createdAt) {
        this.id = id;
        this.name = name;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public int playerCount() {
        return server == null ? 0 : server.playerCount;
    }

    public PersistentServerData copy() {
        PersistentServerData copy = new PersistentServerData(id, name, createdAt);
        copy.updatedAt = updatedAt;
        copy.type = type;
        copy.minMemory = minMemory;
        copy.maxMemory = maxMemory;
        copy.maxPlayers = maxPlayers;
        copy.active = active;
        copy.priority = priority;
        copy.serverhostId = serverhostId;
        copy.source = source;
        copy.workflows = workflows;
        copy.properties.putAll(properties);
        copy.tags.addAll(tags);
        copy.server = server == null ? null : server.copy();
        copy.deleted = deleted;
        return copy;
    }

    public PersistentServer snapshot() {
        return new PersistentServerImpl(copy());
    }
}
