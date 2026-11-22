package app.simplecloud.api.blueprint;

import org.jetbrains.annotations.ApiStatus;

/**
 * Represents the runtime type for server execution.
 */
public enum RuntimeType {
    /**
     * Java runtime environment.
     */
    JAVA,

    /**
     * Native binary executed directly.
     */
    BINARY,

    /**
     * Node.js runtime environment.
     *
     * @deprecated not supported by the controller, will be removed in 1.0.0
     */
    @Deprecated(forRemoval = true)
    @ApiStatus.ScheduledForRemoval(inVersion = "1.0.0")
    NODE,

    /**
     * Python runtime environment.
     *
     * @deprecated not supported by the controller, will be removed in 1.0.0
     */
    @Deprecated(forRemoval = true)
    @ApiStatus.ScheduledForRemoval(inVersion = "1.0.0")
    PYTHON,

    /**
     * Docker container runtime.
     *
     * @deprecated not supported by the controller, will be removed in 1.0.0. Use an image source instead.
     */
    @Deprecated(forRemoval = true)
    @ApiStatus.ScheduledForRemoval(inVersion = "1.0.0")
    DOCKER
}
