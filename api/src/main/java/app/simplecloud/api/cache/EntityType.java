package app.simplecloud.api.cache;

/**
 * Entity types that can be cached.
 * Each entity type can have different cache configuration (stale time, cache time).
 */
public enum EntityType {
    SERVER,
    GROUP,
    PERSISTENT_SERVER,
    PLAYER
}
