package app.simplecloud.api.player;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * One recorded entry of a player's name and skin history.
 *
 * @param id the ID of this profile entry, not the player's UUID
 * @param playerId the player's UUID
 * @param name the player's name at that time
 * @param texture the Base64-encoded skin texture property, or null if the proxy didn't know it
 * @param createdAt when this profile was recorded as an ISO-8601 timestamp, or null if unavailable
 * @see PlayerApi#getProfiles(UUID)
 */
public record PlayerProfile(String id, UUID playerId, String name, @Nullable String texture, @Nullable String createdAt) {}
