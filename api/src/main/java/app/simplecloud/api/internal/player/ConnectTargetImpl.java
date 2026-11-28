package app.simplecloud.api.internal.player;

import app.simplecloud.api.player.ConnectTarget;
import app.simplecloud.api.player.ServerSelectionMode;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * The one implementation of {@link ConnectTarget}. It lives here, not in the API package, so that plugins
 * can't depend on its shape and later releases can change it.
 *
 * @param kind the kind of target
 * @param nameOrId the name or ID the kind names
 * @param selectionMode the selection mode of a group target, or null for the controller's default and for
 *                      targets that aren't groups
 */
public record ConnectTargetImpl(Kind kind, String nameOrId, @Nullable ServerSelectionMode selectionMode)
        implements ConnectTarget {

    public ConnectTargetImpl {
        Objects.requireNonNull(nameOrId, kind.parameterName());
    }

    @Override
    public String toString() {
        String mode = selectionMode != null ? ", selectionMode=" + selectionMode : "";
        return "ConnectTarget[" + kind.parameterName() + "=" + nameOrId + mode + "]";
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
