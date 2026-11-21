package app.simplecloud.api.blueprint;

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
    NODE,

    /**
     * Python runtime environment.
     *
     * @deprecated not supported by the controller, will be removed in 1.0.0
     */
    @Deprecated(forRemoval = true)
    PYTHON,

    /**
     * Docker container runtime.
     *
     * @deprecated not supported by the controller, will be removed in 1.0.0. Use an image source instead.
     */
    @Deprecated(forRemoval = true)
    DOCKER
}
