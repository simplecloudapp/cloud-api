package app.simplecloud.api.persistentserver;

import app.simplecloud.api.base.ServerBase;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a persistent server configuration.
 *
 * <p>Persistent servers are long-lived server instances that maintain state across restarts.
 * Unlike servers spawned from a group, persistent servers don't have scaling or deployment
 * configuration - they are single instances that run on a specific host.
 *
 * <p>This interface extends {@link ServerBase} to provide common configuration access
 * alongside persistent server-specific properties.
 */
public interface PersistentServer extends ServerBase {

    /**
     * Returns the unique identifier of this persistent server.
     *
     * @return the persistent server ID
     */
    String getPersistentServerId();

    /**
     * Returns whether this persistent server is active.
     *
     * <p>An active persistent server will be automatically started when there's no
     * running instance.
     *
     * @return true if active, false otherwise, or null if not set
     */
    @Nullable Boolean isActive();

    /**
     * Returns the ID of the serverhost this persistent server is assigned to.
     *
     * @return the serverhost ID, or null if not set
     */
    @Nullable String getServerhostId();
}

