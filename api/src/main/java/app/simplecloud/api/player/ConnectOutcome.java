package app.simplecloud.api.player;

import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * How a connect from {@link PlayerApi#connectTo(java.util.UUID, ConnectTarget)} ended, and on which server.
 */
public final class ConnectOutcome {

    private final ConnectStatus status;
    private final @Nullable String serverName;

    private ConnectOutcome(ConnectStatus status, @Nullable String serverName) {
        this.status = Objects.requireNonNull(status, "status");
        this.serverName = serverName;
    }

    /**
     * Creates an outcome, for example for a test double.
     *
     * @param status how the connect ended
     * @param serverName the cloud name of the server the connect was last tried on, or null
     * @return the outcome
     */
    public static ConnectOutcome of(ConnectStatus status, @Nullable String serverName) {
        return new ConnectOutcome(status, serverName);
    }

    /**
     * Returns how the connect ended.
     *
     * @return the status
     */
    public ConnectStatus getStatus() {
        return status;
    }

    /**
     * Returns the cloud name of the server the controller last tried, such as {@code lobby-1}. For a group,
     * this is the server the controller picked.
     *
     * @return the server's cloud name, or null when no server was tried, and always for a target from
     * {@link ConnectTarget#registeredServerName(String)}, because it doesn't go through the controller
     */
    public @Nullable String getServerName() {
        return serverName;
    }

    /**
     * Returns whether the player is on the target server now: true for {@link ConnectStatus#SUCCESS} and
     * {@link ConnectStatus#ALREADY_CONNECTED}.
     *
     * @return true if the player is on the target server
     */
    public boolean isConnected() {
        return status == ConnectStatus.SUCCESS || status == ConnectStatus.ALREADY_CONNECTED;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ConnectOutcome outcome)) {
            return false;
        }
        return status == outcome.status && Objects.equals(serverName, outcome.serverName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(status, serverName);
    }

    @Override
    public String toString() {
        return "ConnectOutcome[status=" + status + ", serverName=" + serverName + "]";
    }
}
