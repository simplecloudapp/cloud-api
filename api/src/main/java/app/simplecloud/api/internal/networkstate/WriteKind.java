package app.simplecloud.api.internal.networkstate;

/**
 * The kind of item that the route of an HTTP write writes.
 */
public enum WriteKind {
    SERVER,
    SERVER_GROUP,
    PERSISTENT_SERVER,
    BLUEPRINT
}
