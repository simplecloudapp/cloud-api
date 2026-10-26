package app.simplecloud.api.internal.integration.adventure;

import app.simplecloud.api.internal.nats.FakeNatsConnection;
import build.buf.gen.simplecloud.adventure.v1.AdventureBossBar;
import build.buf.gen.simplecloud.adventure.v1.AdventureComponent;
import build.buf.gen.simplecloud.adventure.v1.SendBossBarRemoveRequest;
import build.buf.gen.simplecloud.adventure.v1.SendBossBarRequest;
import build.buf.gen.simplecloud.adventure.v1.SendMessageRequest;
import io.nats.client.Message;
import io.nats.client.impl.NatsMessage;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AdventureIntegrationTest {

    private static final String NETWORK_ID = "test-network";
    private static final String SERVER_ID = "33333333-3333-3333-3333-333333333333";
    private static final String GROUP_NAME = "lobby";
    private static final UUID PLAYER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_PLAYER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String PLAYER_MESSAGE_SUBSCRIPTION = NETWORK_ID + ".adventure.player.*.message";

    private final RecordingAudience player = new RecordingAudience();
    private final RecordingAudience allPlayers = new RecordingAudience();
    private final FakeNatsConnection nats = FakeNatsConnection.withoutRequests();

    @Test
    void playerMessage_reachesThePlayerOnThisServer() throws Exception {
        startIntegration();

        nats.deliver(PLAYER_MESSAGE_SUBSCRIPTION, messageAction(NETWORK_ID + ".adventure.player." + PLAYER_ID + ".message"));

        assertEquals(List.of(Component.text("Hello")), player.messages);
        assertEquals(List.of(), allPlayers.messages);
    }

    @Test
    void playerMessage_forPlayerOnAnotherServer_isIgnored() throws Exception {
        startIntegration();

        nats.deliver(PLAYER_MESSAGE_SUBSCRIPTION, messageAction(NETWORK_ID + ".adventure.player." + OTHER_PLAYER_ID + ".message"));

        assertEquals(List.of(), player.messages);
        assertEquals(List.of(), allPlayers.messages);
    }

    @Test
    void serverAndGroupMessages_reachAllPlayers() throws Exception {
        startIntegration();
        String serverSubject = NETWORK_ID + ".adventure.server." + SERVER_ID + ".message";
        String groupSubject = NETWORK_ID + ".adventure.group." + GROUP_NAME + ".message";

        nats.deliver(serverSubject, messageAction(serverSubject));
        nats.deliver(groupSubject, messageAction(groupSubject));

        assertEquals(List.of(Component.text("Hello"), Component.text("Hello")), allPlayers.messages);
        assertEquals(List.of(), player.messages);
    }

    @Test
    void bossBarForPlayerOnAnotherServer_isNotRemovedByABroadcast() throws Exception {
        startIntegration();
        String playerBossBarSubject = NETWORK_ID + ".adventure.player." + OTHER_PLAYER_ID + ".bossbar";
        String serverRemoveSubject = NETWORK_ID + ".adventure.server." + SERVER_ID + ".bossbar-remove";
        SendBossBarRequest showBossBar = SendBossBarRequest.newBuilder()
                .setBossBar(AdventureBossBar.newBuilder()
                        .setId("event")
                        .setTitle(AdventureComponent.newBuilder().setJson("{\"text\":\"Event\"}"))
                        .setProgress(1f))
                .build();
        SendBossBarRemoveRequest removeBossBar = SendBossBarRemoveRequest.newBuilder()
                .setBossBarId("event")
                .build();

        nats.deliver(NETWORK_ID + ".adventure.player.*.bossbar", action(playerBossBarSubject, showBossBar.toByteArray()));
        nats.deliver(serverRemoveSubject, action(serverRemoveSubject, removeBossBar.toByteArray()));

        // The boss bar was never registered here, so there is nothing to hide.
        assertEquals(List.of(), allPlayers.hiddenBossBars);
    }

    private void startIntegration() {
        PlayerAudiences playerAudiences = new FakePlayerAudiences(Map.of(PLAYER_ID, player), allPlayers);
        AdventureIntegration integration = new AdventureIntegration(
                nats.connection(), NETWORK_ID, playerAudiences, SERVER_ID, GROUP_NAME);
        integration.start();
    }

    private static Message messageAction(String subject) {
        SendMessageRequest request = SendMessageRequest.newBuilder()
                .setMessage(AdventureComponent.newBuilder().setJson("{\"text\":\"Hello\"}"))
                .build();
        return action(subject, request.toByteArray());
    }

    private static Message action(String subject, byte[] data) {
        return NatsMessage.builder()
                .subject(subject)
                .data(data)
                .build();
    }

    private static final class RecordingAudience implements Audience {

        private final List<Component> messages = new ArrayList<>();
        private final List<BossBar> hiddenBossBars = new ArrayList<>();

        @Override
        public void sendMessage(Component message) {
            messages.add(message);
        }

        @Override
        public void hideBossBar(BossBar bar) {
            hiddenBossBars.add(bar);
        }
    }

    private static final class FakePlayerAudiences implements PlayerAudiences {

        private final Map<UUID, Audience> players;
        private final Audience allPlayers;

        private FakePlayerAudiences(Map<UUID, Audience> players, Audience allPlayers) {
            this.players = players;
            this.allPlayers = allPlayers;
        }

        @Override
        public Optional<Audience> findPlayer(UUID playerId) {
            Audience player = players.get(playerId);
            return Optional.ofNullable(player);
        }

        @Override
        public Audience allPlayers() {
            return allPlayers;
        }
    }
}
