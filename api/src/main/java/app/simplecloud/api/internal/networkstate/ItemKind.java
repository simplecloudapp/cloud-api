package app.simplecloud.api.internal.networkstate;

/**
 * The four kinds of item a network copy holds. A read of a kind returns items of that kind; a write of a
 * kind is a route that writes an item of that kind.
 */
public enum ItemKind {
    SERVER,
    SERVER_GROUP,
    PERSISTENT_SERVER,
    BLUEPRINT
}
