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
     * Native binary executable.
     */
    BINARY,

    /**
     * Node.js runtime environment.
     *
     * @deprecated the controller does not support this runtime and rejects it
     */
    @Deprecated
    NODE,

    /**
     * Python runtime environment.
     *
     * @deprecated the controller does not support this runtime and rejects it
     */
    @Deprecated
    PYTHON,

    /**
     * Docker container runtime.
     *
     * @deprecated the controller does not support this runtime and rejects it
     */
    @Deprecated
    DOCKER
}
