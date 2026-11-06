package app.simplecloud.api.cache;

import java.util.Arrays;
import java.util.Objects;

/**
 * Identifies a cached query by a list of parts.
 *
 * <p>Keys are hierarchical: a key matches every key that starts with the same parts,
 * see {@link QueryCache#invalidateAll(QueryKey)}. The API uses {@code "server"}, {@code "group"} and
 * {@code "persistentServer"} as first part for single entities, and their plural for lists, for example
 * {@code QueryKey.of("server", serverId)} or {@code QueryKey.of("servers")}.
 */
public final class QueryKey {

    private final Object[] parts;
    private final int hashCode;

    private QueryKey(Object[] parts) {
        this.parts = parts;
        this.hashCode = Arrays.deepHashCode(parts);
    }

    /**
     * Creates a query key from the given parts.
     *
     * @param parts the key parts
     * @return a new QueryKey
     */
    public static QueryKey of(Object... parts) {
        return new QueryKey(parts);
    }

    /**
     * Returns the entity type inferred from the first part, for example {@code "servers"} for
     * {@link EntityType#SERVER}.
     *
     * @return the entity type, or null if unknown
     */
    public EntityType getEntityType() {
        if (parts.length == 0) return null;
        String first = parts[0].toString();
        return switch (first) {
            case "server", "servers" -> EntityType.SERVER;
            case "group", "groups" -> EntityType.GROUP;
            case "persistentServer", "persistentServers" -> EntityType.PERSISTENT_SERVER;
            case "player", "players" -> EntityType.PLAYER;
            default -> null;
        };
    }

    /**
     * Returns the key parts.
     */
    public Object[] getParts() {
        return parts.clone();
    }

    /**
     * Checks if this key matches or is a parent of the given key.
     * Used for invalidation patterns.
     *
     * <p>Example: {@code QueryKey.of("servers")} matches {@code QueryKey.of("servers", "group", "Lobby")}
     *
     * @param other the key to check against
     * @return true if this key is a prefix of the other key
     */
    public boolean matches(QueryKey other) {
        if (parts.length > other.parts.length) return false;
        for (int i = 0; i < parts.length; i++) {
            if (!Objects.equals(parts[i], other.parts[i])) return false;
        }
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        QueryKey queryKey = (QueryKey) o;
        return Arrays.deepEquals(parts, queryKey.parts);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public String toString() {
        return "QueryKey" + Arrays.toString(parts);
    }
}
