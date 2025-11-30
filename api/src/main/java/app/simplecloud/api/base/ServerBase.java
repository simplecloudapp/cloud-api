package app.simplecloud.api.base;

import app.simplecloud.api.group.GroupServerType;
import app.simplecloud.api.group.SourceConfig;
import app.simplecloud.api.group.WorkflowsConfig;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Common base interface for server configurations.
 *
 * <p>This interface represents the shared configuration between {@link app.simplecloud.api.group.Group}
 * and {@link app.simplecloud.api.persistentserver.PersistentServer}. A server can be spawned from either
 * a group (with scaling and deployment configuration) or a persistent server (a single long-lived instance).
 *
 * <p>Use this interface when you need to access common server configuration properties regardless
 * of whether the server was spawned from a group or persistent server.
 */
public interface ServerBase {

    /**
     * Returns the human-readable name of this server configuration.
     *
     * @return the name
     */
    String getName();

    /**
     * Returns the minimum memory allocation (in MB).
     *
     * @return the minimum memory, or null if not set
     */
    @Nullable Integer getMinMemory();

    /**
     * Returns the maximum memory allocation (in MB).
     *
     * @return the maximum memory, or null if not set
     */
    @Nullable Integer getMaxMemory();

    /**
     * Returns the maximum number of players.
     *
     * @return the max players, or null if not set
     */
    @Nullable Integer getMaxPlayers();

    /**
     * Returns the source configuration.
     *
     * <p>Defines whether servers use a blueprint or container image.
     *
     * @return the source config, or null if not set
     */
    @Nullable SourceConfig getSource();

    /**
     * Returns the workflows configuration.
     *
     * <p>Defines automated workflows to run on server lifecycle events.
     *
     * @return the workflows config, or null if not set
     */
    @Nullable WorkflowsConfig getWorkflows();

    /**
     * Returns custom properties.
     *
     * @return the properties map, or null if none set
     */
    @Nullable Map<String, Object> getProperties();

    /**
     * Returns tags for organization and filtering.
     *
     * @return the list of tags, or null if none set
     */
    @Nullable List<String> getTags();

    /**
     * Returns the type (SERVER or PROXY).
     *
     * @return the server type
     */
    GroupServerType getType();

    /**
     * Returns the timestamp when this was created (ISO 8601 format).
     *
     * @return the creation timestamp
     */
    String getCreatedAt();

    /**
     * Returns the timestamp when this was last updated (ISO 8601 format).
     *
     * @return the update timestamp
     */
    String getUpdatedAt();

    /**
     * Returns whether this is a group configuration.
     *
     * @return true if this is a group, false if it's a persistent server
     */
    default boolean isGroup() {
        return this instanceof app.simplecloud.api.group.Group;
    }

    /**
     * Returns whether this is a persistent server configuration.
     *
     * @return true if this is a persistent server, false if it's a group
     */
    default boolean isPersistentServer() {
        return this instanceof app.simplecloud.api.persistentserver.PersistentServer;
    }
}

