package app.simplecloud.api.testing.internal.player;

import app.simplecloud.api.player.CloudPlayer;
import app.simplecloud.api.player.UpdateClientSettingsRequest;
import app.simplecloud.api.server.Server;
import app.simplecloud.api.testing.internal.CloudState;
import app.simplecloud.api.testing.player.TestPlayer;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.jetbrains.annotations.Nullable;

public final class TestPlayerImpl extends AbstractPlayer implements TestPlayer {

    private final CloudState cloud;
    private final PlayerData data;

    public TestPlayerImpl(CloudState cloud, PlayerData data) {
        super(cloud.playerApi(), data.inbox);
        this.cloud = cloud;
        this.data = data;
    }

    @Override
    public UUID getUniqueId() {
        return data.id;
    }

    @Override
    public String getName() {
        return cloud.read(() -> data.name);
    }

    @Override
    public String getDisplayName() {
        return cloud.read(() -> data.displayName);
    }

    @Override
    public String getConnectedProxyName() {
        return cloud.read(data::proxyName);
    }

    @Override
    public String getConnectedServerName() {
        return cloud.read(data::serverName);
    }

    @Override
    public boolean isOnline() {
        return cloud.read(data::online);
    }

    @Override
    public long getOnlineTimeSeconds() {
        return cloud.read(() -> data.onlineTimeSeconds(cloud.now()));
    }

    @Override
    public String getSessionId() {
        return cloud.read(data::sessionId);
    }

    @Override
    public String getFirstSeen() {
        return cloud.read(() -> data.firstSeen.toString());
    }

    @Override
    public String getLastSeen() {
        return cloud.read(() -> data.lastSeen.toString());
    }

    @Override
    public Map<String, String> getProperties() {
        return cloud.read(() -> Map.copyOf(data.properties));
    }

    @Override
    public TestPlayer login() {
        cloud.login(data.id, null);
        return this;
    }

    @Override
    public TestPlayer login(Server proxy) {
        Objects.requireNonNull(proxy, "proxy");
        cloud.login(data.id, proxy.getServerId());
        return this;
    }

    @Override
    public TestPlayer join(Server server) {
        Objects.requireNonNull(server, "server");
        cloud.join(data.id, server.getServerId());
        return this;
    }

    @Override
    public void disconnect() {
        cloud.disconnect(data.id);
    }

    @Override
    public TestPlayer setDisplayName(String displayName) {
        Objects.requireNonNull(displayName, "displayName");
        cloud.modifyPlayer(data.id, player -> player.displayName = displayName);
        return this;
    }

    @Override
    public TestPlayer changeProfile(String name, @Nullable String texture) {
        cloud.changeProfile(data.id, name, texture);
        return this;
    }

    @Override
    public TestPlayer setProperty(String key, String value) {
        cloud.updatePlayerProperties(data.id, Map.of(key, value));
        return this;
    }

    @Override
    public TestPlayer removeProperty(String key) {
        cloud.deletePlayerProperties(data.id, List.of(key));
        return this;
    }

    @Override
    public TestPlayer updateClientSettings(UpdateClientSettingsRequest request) {
        cloud.updateClientSettings(data.id, Objects.requireNonNull(request, "request"));
        return this;
    }

    @Override
    public List<Component> messages() {
        return inbox.messages();
    }

    @Override
    public List<Component> actionBars() {
        return inbox.actionBars();
    }

    @Override
    public List<Title> titles() {
        return inbox.titles();
    }

    @Override
    public void clearReceived() {
        inbox.clear();
    }

    @Override
    public CloudPlayer snapshot() {
        return cloud.snapshot(data, cloud.playerApi());
    }

    @Override
    public String toString() {
        return "TestPlayer[" + getName() + ", " + data.id + "]";
    }
}
