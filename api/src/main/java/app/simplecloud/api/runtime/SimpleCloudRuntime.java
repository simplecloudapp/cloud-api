package app.simplecloud.api.runtime;

/**
 * Resolves the runtime identity exposed by SimpleCloud environment variables.
 */
public final class SimpleCloudRuntime {

    private SimpleCloudRuntime() {}

    /**
     * @return the unique ID of this server from {@code SIMPLECLOUD_UNIQUE_ID}
     */
    public static String serverId() {
        return value("SIMPLECLOUD_UNIQUE_ID");
    }

    /**
     * @return the group name from {@code SIMPLECLOUD_GROUP}
     */
    public static String groupName() {
        return value("SIMPLECLOUD_GROUP");
    }

    /**
     * @return the numerical ID from {@code SIMPLECLOUD_NUMERICAL_ID}
     */
    public static String numericalId() {
        return value("SIMPLECLOUD_NUMERICAL_ID");
    }

    /**
     * Returns the cloud name of this server: {@code <group>-<number>}, or just the group name if there is
     * no numerical ID, or the server ID if there is no group name.
     *
     * @return the server name
     */
    public static String serverName() {
        String groupName = groupName();
        String numericalId = numericalId();

        if (!groupName.isBlank() && !numericalId.isBlank()) {
            return groupName + "-" + numericalId;
        }
        if (!groupName.isBlank()) {
            return groupName;
        }
        return serverId();
    }

    private static String value(String key) {
        String value = System.getenv(key);
        return value == null ? "" : value.trim();
    }
}
