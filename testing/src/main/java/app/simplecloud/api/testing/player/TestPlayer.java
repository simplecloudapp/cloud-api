package app.simplecloud.api.testing.player;

import app.simplecloud.api.player.CloudPlayer;
import app.simplecloud.api.player.UpdateClientSettingsRequest;
import app.simplecloud.api.server.Server;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface TestPlayer extends CloudPlayer {

    TestPlayer login();

    TestPlayer login(Server proxy);

    TestPlayer join(Server server);

    void disconnect();

    TestPlayer setDisplayName(String displayName);

    TestPlayer changeProfile(String name, @Nullable String texture);

    TestPlayer setProperty(String key, String value);

    TestPlayer removeProperty(String key);

    TestPlayer updateClientSettings(UpdateClientSettingsRequest request);

    List<Component> messages();

    List<Component> actionBars();

    List<Title> titles();

    void clearReceived();

    CloudPlayer snapshot();
}
