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
     * Node.js runtime environment.
     */
    NODE,

    /**
     * Python runtime environment.
     */
    PYTHON,

    /**
     * Docker container runtime.
     */
    DOCKER
}

