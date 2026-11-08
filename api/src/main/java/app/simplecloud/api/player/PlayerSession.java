package app.simplecloud.api.player;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * One session of a player on the network, from login to logout.
 *
 * @param id the ID of this session
 * @param networkId the network the session belongs to
 * @param playerId the player's UUID
 * @param addressHash a hash of the address the player connected from, or null if not reported. Useful to
 *     tell whether two sessions came from the same address. It is not a secure hash: for IPv4 most of the
 *     address can be read back from it.
 * @param clientVersion the client's protocol version, for example {@code 765}, or 0 if unknown
 * @param onlineMode whether the proxy authenticated the player with Mojang
 * @param loginTime the login time as an ISO-8601 timestamp
 * @param logoutTime the logout time as an ISO-8601 timestamp, or null while the session is active
 * @param durationSeconds the session length in seconds, or null if the controller didn't report one
 * @param clientSettings the client settings of this session, or null if not loaded
 * @see PlayerApi#getSessions(UUID)
 * @see PlayerApi#getCurrentSession(UUID)
 */
public record PlayerSession(
        String id,
        String networkId,
        UUID playerId,
        @Nullable String addressHash,
        int clientVersion,
        boolean onlineMode,
        String loginTime,
        @Nullable String logoutTime,
        @Nullable Long durationSeconds,
        @Nullable PlayerClientSettings clientSettings) {

    /**
     * @return true if the player hasn't logged out of this session yet
     */
    public boolean isActive() {
        return logoutTime == null;
    }
}
