package app.simplecloud.api.internal.persistentserver;

import app.simplecloud.api.group.GroupServerType;
import app.simplecloud.api.group.SourceConfig;
import app.simplecloud.api.group.WorkflowsConfig;
import app.simplecloud.api.internal.util.ModelConversionUtil;
import app.simplecloud.api.persistentserver.PersistentServer;
import app.simplecloud.api.web.models.ModelsCreatePersistentServerResponse;
import app.simplecloud.api.web.models.ModelsPersistentServerInfo;
import app.simplecloud.api.web.models.ModelsPersistentServerSummary;
import app.simplecloud.api.web.models.ModelsUpdatePersistentServerResponse;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

public record PersistentServerImpl(
        @Nullable String persistentServerId,
        @Nullable String name,
        @Nullable String type,
        @Nullable Integer minMemory,
        @Nullable Integer maxMemory,
        @Nullable Integer maxPlayers,
        @Nullable Boolean active,
        @Nullable Integer priority,
        int playerCount,
        @Nullable String serverhostId,
        @Nullable SourceConfig source,
        @Nullable WorkflowsConfig workflows,
        @Nullable Map<String, Object> properties,
        @Nullable List<String> tags,
        @Nullable String createdAt,
        @Nullable String updatedAt)
        implements PersistentServer {

    public static PersistentServerImpl from(ModelsPersistentServerSummary summary) {
        return new PersistentServerImpl(
                summary.getPersistentServerId(),
                summary.getName(),
                summary.getType(),
                summary.getMinMemory(),
                summary.getMaxMemory(),
                summary.getMaxPlayers(),
                summary.getActive(),
                summary.getPriority(),
                playerCount(summary.getPlayerCount()),
                summary.getServerhostId(),
                ModelConversionUtil.fromModel(summary.getSource()),
                ModelConversionUtil.fromModel(summary.getWorkflows()),
                summary.getProperties(),
                summary.getTags(),
                summary.getCreatedAt(),
                summary.getUpdatedAt());
    }

    public static PersistentServerImpl from(ModelsCreatePersistentServerResponse response) {
        return new PersistentServerImpl(
                response.getPersistentServerId(),
                response.getName(),
                response.getType(),
                response.getMinMemory(),
                response.getMaxMemory(),
                response.getMaxPlayers(),
                response.getActive(),
                response.getPriority(),
                playerCount(response.getPlayerCount()),
                response.getServerhostId(),
                ModelConversionUtil.fromModel(response.getSource()),
                ModelConversionUtil.fromModel(response.getWorkflows()),
                response.getProperties(),
                response.getTags(),
                response.getCreatedAt(),
                response.getUpdatedAt());
    }

    public static PersistentServerImpl from(ModelsUpdatePersistentServerResponse response) {
        return new PersistentServerImpl(
                response.getPersistentServerId(),
                response.getName(),
                response.getType(),
                response.getMinMemory(),
                response.getMaxMemory(),
                response.getMaxPlayers(),
                response.getActive(),
                response.getPriority(),
                playerCount(response.getPlayerCount()),
                response.getServerhostId(),
                ModelConversionUtil.fromModel(response.getSource()),
                ModelConversionUtil.fromModel(response.getWorkflows()),
                response.getProperties(),
                response.getTags(),
                response.getCreatedAt(),
                response.getUpdatedAt());
    }

    public static PersistentServerImpl from(ModelsPersistentServerInfo info, int playerCount) {
        return new PersistentServerImpl(
                info.getId(),
                info.getName(),
                info.getType(),
                info.getMinMemory(),
                info.getMaxMemory(),
                info.getMaxPlayers(),
                info.getActive(),
                null,
                playerCount,
                info.getServerhostId(),
                ModelConversionUtil.fromModel(info.getSource()),
                ModelConversionUtil.fromModel(info.getWorkflows()),
                info.getProperties(),
                info.getTags(),
                info.getCreatedAt(),
                info.getUpdatedAt());
    }

    @Override
    public String getPersistentServerId() {
        return require(persistentServerId, "Persistent server ID");
    }

    @Override
    public String getName() {
        return require(name, "Persistent server name");
    }

    @Override
    public GroupServerType getType() {
        String value = require(type, "Persistent server type");
        try {
            return GroupServerType.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Invalid persistent server type: " + value, e);
        }
    }

    @Override
    public @Nullable Integer getMinMemory() {
        return minMemory;
    }

    @Override
    public @Nullable Integer getMaxMemory() {
        return maxMemory;
    }

    @Override
    public @Nullable Integer getMaxPlayers() {
        return maxPlayers;
    }

    @Override
    public @Nullable Boolean isActive() {
        return active;
    }

    @Override
    public @Nullable Integer getPriority() {
        return priority;
    }

    @Override
    public int getPlayerCount() {
        return playerCount;
    }

    @Override
    public @Nullable String getServerhostId() {
        return serverhostId;
    }

    @Override
    public @Nullable SourceConfig getSource() {
        return source;
    }

    @Override
    public @Nullable WorkflowsConfig getWorkflows() {
        return workflows;
    }

    @Override
    public @Nullable Map<String, Object> getProperties() {
        return properties;
    }

    @Override
    public @Nullable List<String> getTags() {
        return tags;
    }

    @Override
    public String getCreatedAt() {
        return require(createdAt, "Created at timestamp");
    }

    @Override
    public String getUpdatedAt() {
        return require(updatedAt, "Updated at timestamp");
    }

    private static int playerCount(@Nullable Integer count) {
        return count != null ? Math.max(count, 0) : 0;
    }

    private static <T> T require(@Nullable T value, String name) {
        if (value == null) {
            throw new IllegalStateException(name + " is null");
        }
        return value;
    }
}
