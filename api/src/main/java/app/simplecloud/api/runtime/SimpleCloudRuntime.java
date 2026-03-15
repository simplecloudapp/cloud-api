package app.simplecloud.api.runtime;

/**
 * Resolves the runtime identity exposed by SimpleCloud environment variables.
 */
public final class SimpleCloudRuntime {

    private SimpleCloudRuntime() {
    }

    public static String serverId() {
        return value("SIMPLECLOUD_UNIQUE_ID");
    }

    public static String groupName() {
        return value("SIMPLECLOUD_GROUP");
    }

    public static String numericalId() {
        return value("SIMPLECLOUD_NUMERICAL_ID");
    }

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
