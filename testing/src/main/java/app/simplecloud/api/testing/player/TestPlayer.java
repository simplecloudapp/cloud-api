package app.simplecloud.api.testing.player;

import app.simplecloud.api.player.CloudPlayer;
import app.simplecloud.api.player.UpdateClientSettingsRequest;
import app.simplecloud.api.server.Server;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A player in the test cloud.
 *
 * <pre>{@code
 * TestPlayer steve = cloud.createPlayer("jskrsh").login(proxy).join(lobby);
 * plugin.greet(steve.getUniqueId());
 * assertEquals(List.of(Component.text("Welcome!")), steve.messages());
 * }</pre>
 */
public interface TestPlayer extends CloudPlayer {

    /**
     * Logs the player in without proxy and server.
     *
     * @throws IllegalStateException if the player is already online
     */
    TestPlayer login();

    /**
     * Logs the player in through the given proxy.
     *
     * @throws IllegalStateException if the player is already online
     */
    TestPlayer login(Server proxy);

    /**
     * Moves the player to the server and logs them in first if needed.
     */
    TestPlayer join(Server server);

    /**
     * Disconnects the player and ends the session.
     *
     * @throws IllegalStateException if the player is offline
     */
    void disconnect();

    /**
     * Sets the display name.
     */
    TestPlayer setDisplayName(String displayName);

    /**
     * Records a new profile, for example after a name change.
     */
    TestPlayer changeProfile(String name, @Nullable String texture);

    /**
     * Sets a single property.
     */
    TestPlayer setProperty(String key, String value);

    /**
     * Removes a single property.
     */
    TestPlayer removeProperty(String key);

    /**
     * Updates the client settings as if the client sent them.
     */
    TestPlayer updateClientSettings(UpdateClientSettingsRequest request);

    /**
     * Returns all chat messages the player received.
     */
    List<Component> messages();

    /**
     * Returns all action bars the player received.
     */
    List<Component> actionBars();

    /**
     * Returns all titles the player received.
     */
    List<Title> titles();

    /**
     * Forgets all received messages, action bars and titles.
     */
    void clearReceived();

    /**
     * Returns an immutable copy of the current state.
     */
    CloudPlayer snapshot();
}
