package app.simplecloud.api.group;

/**
 * Represents the source type for server instances.
 */
public enum SourceType {
    /** Servers are created from a blueprint configuration. */
    BLUEPRINT,
    
    /** Servers are created from a container image. */
    IMAGE
}

