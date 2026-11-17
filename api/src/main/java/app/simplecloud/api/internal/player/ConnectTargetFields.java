package app.simplecloud.api.internal.player;

import app.simplecloud.api.player.ConnectTarget;
import app.simplecloud.api.player.ServerSelectionMode;
import org.jetbrains.annotations.Nullable;

/**
 * What a {@link ConnectTarget} holds. {@code ConnectTarget} keeps it private, so that plugins can't depend on
 * its shape and later releases can change it, and registers a {@link Reader} for it when its class loads.
 *
 * @param kind the kind of target
 * @param nameOrId the name or ID the kind names
 * @param selectionMode the selection mode of a group target, or null for the controller's default and for
 *                      targets that aren't groups
 */
public record ConnectTargetFields(Kind kind, String nameOrId, @Nullable ServerSelectionMode selectionMode) {

    private static volatile @Nullable Reader reader;

    /**
     * Registers how to read a target's fields. Only {@code ConnectTarget} calls this, once, when its class
     * loads.
     *
     * @param targetReader reads the fields of a target
     * @throws IllegalStateException if a reader is already registered
     */
    public static synchronized void registerReader(Reader targetReader) {
        if (reader != null) {
            throw new IllegalStateException("A ConnectTarget reader is already registered");
        }
        reader = targetReader;
    }

    // A ConnectTarget instance exists, so its class has loaded and registered the reader.
    static ConnectTargetFields readFrom(ConnectTarget target) {
        return reader.fieldsOf(target);
    }

    /**
     * Reads the private fields of a {@link ConnectTarget}.
     */
    public interface Reader {
        ConnectTargetFields fieldsOf(ConnectTarget target);
    }

    public enum Kind {
        SERVER_ID("serverId"),
        SERVER_NAME("serverName"),
        GROUP_ID("groupId"),
        GROUP_NAME("groupName"),
        PERSISTENT_SERVER_ID("persistentServerId"),
        REGISTERED_SERVER_NAME("registeredServerName");

        private final String parameterName;

        Kind(String parameterName) {
            this.parameterName = parameterName;
        }

        /**
         * Returns the name of the factory parameter that takes this kind's name or ID, for messages.
         *
         * @return the parameter name
         */
        public String parameterName() {
            return parameterName;
        }
    }
}
