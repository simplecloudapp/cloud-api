package app.simplecloud.api.testing.internal.player;

import app.simplecloud.api.CloudApiException;
import app.simplecloud.api.player.CloudPlayer;
import app.simplecloud.api.player.PlayerApi;
import app.simplecloud.api.player.ServerSelectionMode;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.chat.ChatType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;

public abstract class AbstractPlayer implements CloudPlayer {

    private final PlayerApi api;
    protected final Inbox inbox;

    protected AbstractPlayer(PlayerApi api, Inbox inbox) {
        this.api = api;
        this.inbox = inbox;
    }

    @Override
    public void sendMessage(Component message) {
        inbox.message(message);
    }

    @Override
    public void sendMessage(Component message, ChatType.Bound boundChatType) {
        inbox.message(message);
    }

    @Override
    public void sendActionBar(Component message) {
        inbox.actionBar(message);
    }

    @Override
    public void showTitle(Title title) {
        inbox.title(title);
    }

    @Override
    public CompletableFuture<Void> kick(Component reason) {
        return api.kick(getUniqueId(), reason).thenApply(success -> {
            if (!Boolean.TRUE.equals(success)) {
                throw new CloudApiException(
                        CloudApiException.Reason.UNKNOWN, "Player '" + getUniqueId() + "' could not be kicked");
            }
            return null;
        });
    }

    @Override
    public CompletableFuture<ConnectResult> connect(String serverName) {
        return api.connect(getUniqueId(), serverName);
    }

    @Override
    public CompletableFuture<ConnectResult> connectDirect(String serverName) {
        return api.connectDirect(getUniqueId(), serverName);
    }

    @Override
    public CompletableFuture<ConnectResult> connectToGroup(String groupName, ServerSelectionMode selectionMode) {
        return api.connectToGroup(getUniqueId(), groupName, selectionMode);
    }

    @Override
    public CompletableFuture<ConnectResult> connectToPersistentServer(String persistentServerId) {
        return api.connectToPersistentServer(getUniqueId(), persistentServerId);
    }
}
