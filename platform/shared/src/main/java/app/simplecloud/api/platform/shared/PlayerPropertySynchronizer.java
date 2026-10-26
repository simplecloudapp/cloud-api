package app.simplecloud.api.platform.shared;

import java.util.UUID;

/**
 * Mirrors player data from another plugin into SimpleCloud player properties.
 */
public interface PlayerPropertySynchronizer {

    /**
     * Used when no supported plugin is installed.
     */
    PlayerPropertySynchronizer NONE = new PlayerPropertySynchronizer() {
        @Override
        public void synchronize(UUID playerId) {
        }

        @Override
        public void forget(UUID playerId) {
        }

        @Override
        public void stop() {
        }
    };

    /**
     * Starts synchronization after the controller acknowledges this player's login.
     */
    void synchronize(UUID playerId);

    /**
     * Stops synchronization for a player who left.
     */
    void forget(UUID playerId);

    /**
     * Stops synchronization for all players when the plugin shuts down.
     */
    void stop();
}
