package app.simplecloud.api.player;

/**
 * How a connect from {@link PlayerApi#connectTo(java.util.UUID, ConnectTarget)} ended.
 *
 * <p>Later releases may add values, so a {@code switch} over this enum needs a default case.
 * {@link ConnectOutcome#isConnected()} keeps its meaning when values are added.
 */
public enum ConnectStatus {

    /**
     * The player was moved to the server.
     */
    SUCCESS,

    /**
     * The player already was on the server.
     */
    ALREADY_CONNECTED,

    /**
     * The target exists, but has no game server in state {@code AVAILABLE}, so the player was not sent
     * anywhere. Examples: an empty group, a server that is starting or stopping, a persistent server that
     * is not running, or a proxy such as {@code proxy-1}, because a proxy is not a game server.
     */
    NO_SERVER_AVAILABLE,

    /**
     * The player's proxy doesn't know the server. For a target from
     * {@link ConnectTarget#registeredServerName(String)}, no server is registered under that name. For
     * every other target, the proxy can't map the server's ID to one of its server names, for example
     * because it has no plugin that fills {@link app.simplecloud.api.proxy.ProxyServerNames}, such as
     * server-connection {@code 1.2.0} or newer, or because the server has only just started and isn't
     * registered yet.
     */
    SERVER_NOT_REGISTERED,

    /**
     * The cloud doesn't know the target: no server, group or persistent server has that name or ID.
     * A server ID passed as a name counts as unknown.
     */
    TARGET_NOT_FOUND,

    /**
     * The player is not online. Only targets that go through the controller answer this; for a target from
     * {@link ConnectTarget#registeredServerName(String)}, no proxy answers and the future fails.
     */
    PLAYER_NOT_ONLINE,

    /**
     * The player's proxy tried to move the player, and that failed, for example because the server
     * refused the player.
     */
    FAILED
}
