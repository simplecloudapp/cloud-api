package app.simplecloud.api.internal.networkstate;

/**
 * The kinds of list and lookup reads a view can answer from its copy. Blueprint reads are not among them:
 * {@code GET /v0/blueprints} has another shape than the blueprint inside a group, so the copy keeps
 * blueprints only to build groups and servers.
 */
public enum ReadKind {
    SERVER,
    SERVER_GROUP,
    PERSISTENT_SERVER
}
