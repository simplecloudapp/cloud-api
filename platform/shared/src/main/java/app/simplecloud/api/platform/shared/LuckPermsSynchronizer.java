package app.simplecloud.api.platform.shared;

import java.util.UUID;

/**
 * Synchronizes the LuckPerms data of the players on this proxy into their cloud properties.
 *
 * <p>LuckPerms is optional, so a proxy that doesn't have it uses {@link #DISABLED}, and its listeners
 * call the synchronizer the same way either way.
 */
public interface LuckPermsSynchronizer {

    /**
     * Does nothing. Used when LuckPerms isn't installed or its API isn't available.
     */
    LuckPermsSynchronizer DISABLED = new LuckPermsSynchronizer() {
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
