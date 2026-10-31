package app.simplecloud.api.internal.player;

import app.simplecloud.api.CloudApiOptions;
import app.simplecloud.api.internal.nats.FakeNatsConnection;
import app.simplecloud.api.player.CloudPlayer;
import app.simplecloud.api.web.apis.PlayersApi;
import app.simplecloud.api.web.models.ModelsPatchPlayerRequest;
import app.simplecloud.api.web.models.ModelsPlayerOnlineTimeResponse;
import app.simplecloud.api.web.models.ModelsPlayerResponse;
import build.buf.gen.simplecloud.player.v2.ConnectPlayerRequest;
import build.buf.gen.simplecloud.player.v2.ConnectPlayerResponse;
import build.buf.gen.simplecloud.player.v2.ConnectResult;
import io.nats.client.Message;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlayerApiImplTest {

    private static final UUID PLAYER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String CONNECT_SUBJECT = "network.player." + PLAYER_ID + ".connect";

    @Test
    void getOnlineTimeSeconds_readsControllerOnlineTime() {
        FakePlayersApi playersApi = new FakePlayersApi();
        playersApi.onlineTimeSeconds = 123;
        PlayerApiImpl api = new PlayerApiImpl(options(), null, playersApi);

        long onlineTime = api.getOnlineTimeSeconds(PLAYER_ID).join();

        assertEquals(123L, onlineTime);
        assertEquals(1, playersApi.onlineTimeGetCalls);
        assertEquals(PLAYER_ID.toString(), playersApi.lastOnlineTimePlayerId);
    }

    @Test
    void setOnlineTimeSeconds_patchesControllerAndReturnsUpdatedPlayer() {
        FakePlayersApi playersApi = new FakePlayersApi();
        PlayerApiImpl api = new PlayerApiImpl(options(), null, playersApi);

        CloudPlayer player = api.setOnlineTimeSeconds(PLAYER_ID, 456).join();

        assertEquals(1, playersApi.patchCalls);
        assertEquals(PLAYER_ID.toString(), playersApi.lastPatchPlayerId);
        assertEquals(Integer.valueOf(456), playersApi.lastPatchRequest.getOnlineTimeSeconds());
        assertEquals(456L, player.getOnlineTimeSeconds());
    }

    @Test
    void resetFirstSeen_patchesOnlyTheFirstSeenReset() {
        FakePlayersApi playersApi = new FakePlayersApi();
        PlayerApiImpl api = new PlayerApiImpl(options(), null, playersApi);

        api.resetFirstSeen(PLAYER_ID).join();

        assertEquals(PLAYER_ID.toString(), playersApi.lastPatchPlayerId);
        assertEquals(Boolean.TRUE, playersApi.lastPatchRequest.getResetFirstSeen());
        assertNull(playersApi.lastPatchRequest.getResetLastSeen());
        assertNull(playersApi.lastPatchRequest.getOnlineTimeSeconds());
    }

    @Test
    void resetLastSeen_patchesOnlyTheLastSeenReset() {
        FakePlayersApi playersApi = new FakePlayersApi();
        PlayerApiImpl api = new PlayerApiImpl(options(), null, playersApi);

        api.resetLastSeen(PLAYER_ID).join();

        assertEquals(PLAYER_ID.toString(), playersApi.lastPatchPlayerId);
        assertEquals(Boolean.TRUE, playersApi.lastPatchRequest.getResetLastSeen());
        assertNull(playersApi.lastPatchRequest.getResetFirstSeen());
        assertNull(playersApi.lastPatchRequest.getOnlineTimeSeconds());
    }

    @Test
    void addOnlineTimeSeconds_readsCurrentValueAndPatchesSum() {
        FakePlayersApi playersApi = new FakePlayersApi();
        playersApi.onlineTimeSeconds = 100;
        PlayerApiImpl api = new PlayerApiImpl(options(), null, playersApi);

        CloudPlayer player = api.addOnlineTimeSeconds(PLAYER_ID, 25).join();

        assertEquals(Integer.valueOf(125), playersApi.lastPatchRequest.getOnlineTimeSeconds());
        assertEquals(125L, player.getOnlineTimeSeconds());
    }

    @Test
    void removeOnlineTimeSeconds_clampsAtZero() {
        FakePlayersApi playersApi = new FakePlayersApi();
        playersApi.onlineTimeSeconds = 100;
        PlayerApiImpl api = new PlayerApiImpl(options(), null, playersApi);

        CloudPlayer player = api.removeOnlineTimeSeconds(PLAYER_ID, 150).join();

        assertEquals(Integer.valueOf(0), playersApi.lastPatchRequest.getOnlineTimeSeconds());
        assertEquals(0L, player.getOnlineTimeSeconds());
    }

    @Test
    void setOnlineTimeSeconds_rejectsValuesControllerCannotStore() {
        PlayerApiImpl api = new PlayerApiImpl(options(), null, new FakePlayersApi());

        CompletionException negative = assertThrows(CompletionException.class, () -> api.setOnlineTimeSeconds(PLAYER_ID, -1).join());
        CompletionException tooLarge = assertThrows(CompletionException.class, () -> api.setOnlineTimeSeconds(PLAYER_ID, (long) Integer.MAX_VALUE + 1L).join());

        assertInstanceOf(IllegalArgumentException.class, negative.getCause());
        assertInstanceOf(IllegalArgumentException.class, tooLarge.getCause());
    }

    @Test
    void connectDirect_sendsServerNameToPlayerProxy() {
        List<String> subjects = new ArrayList<>();
        List<ConnectPlayerRequest> requests = new ArrayList<>();
        FakeNatsConnection nats = FakeNatsConnection.answering((subject, data, timeout) -> {
            subjects.add(subject);
            requests.add(ConnectPlayerRequest.parseFrom(data));
            return connectResponse(ConnectResult.CONNECT_RESULT_SUCCESS);
        });
        PlayerApiImpl api = new PlayerApiImpl(options(), nats.connection(), new FakePlayersApi());

        CloudPlayer.ConnectResult result = api.connectDirect(PLAYER_ID, "lobby-1").join();

        assertEquals(CloudPlayer.ConnectResult.SUCCESS, result);
        assertEquals(List.of(CONNECT_SUBJECT), subjects);
        assertEquals(List.of(expectedConnectRequest("lobby-1")), requests);
    }

    @Test
    void connectDirect_withoutReply_failsConnection() {
        FakeNatsConnection nats = FakeNatsConnection.answering((subject, data, timeout) -> null);
        PlayerApiImpl api = new PlayerApiImpl(options(), nats.connection(), new FakePlayersApi());

        CloudPlayer.ConnectResult result = api.connectDirect(PLAYER_ID, "lobby-1").join();

        assertEquals(CloudPlayer.ConnectResult.CONNECTION_FAILED, result);
    }

    @Test
    void connect_sendsTheSameRequestAsConnectDirect() {
        List<String> subjects = new ArrayList<>();
        List<ConnectPlayerRequest> requests = new ArrayList<>();
        FakeNatsConnection nats = FakeNatsConnection.answering((subject, data, timeout) -> {
            subjects.add(subject);
            requests.add(ConnectPlayerRequest.parseFrom(data));
            return connectResponse(ConnectResult.CONNECT_RESULT_SERVER_NOT_FOUND);
        });
        PlayerApiImpl api = new PlayerApiImpl(options(), nats.connection(), new FakePlayersApi());

        CloudPlayer.ConnectResult result = api.connect(PLAYER_ID, "lobby-2").join();

        assertEquals(CloudPlayer.ConnectResult.SERVER_NOT_FOUND, result);
        assertEquals(List.of(CONNECT_SUBJECT), subjects);
        assertEquals(List.of(expectedConnectRequest("lobby-2")), requests);
    }

    private static ConnectPlayerRequest expectedConnectRequest(String serverName) {
        return ConnectPlayerRequest.newBuilder()
                .setPlayerId(PLAYER_ID.toString())
                .setServerName(serverName)
                .build();
    }

    private static Message connectResponse(ConnectResult result) {
        ConnectPlayerResponse response = ConnectPlayerResponse.newBuilder().setResult(result).build();
        return FakeNatsConnection.reply(response.toByteArray());
    }

    private static CloudApiOptions options() {
        return CloudApiOptions.builder()
                .controllerUrl("http://localhost")
                .networkId("network")
                .networkSecret("secret")
                .build();
    }

    private static ModelsPlayerResponse playerResponse(int onlineTimeSeconds) {
        ModelsPlayerResponse response = new ModelsPlayerResponse();
        response.setId(PLAYER_ID.toString());
        response.setName("Philipp");
        response.setDisplayName("Philipp");
        response.setOnline(true);
        response.setOnlineTimeSeconds(onlineTimeSeconds);
        return response;
    }

    private static final class FakePlayersApi extends PlayersApi {

        private int onlineTimeSeconds;
        private int onlineTimeGetCalls;
        private int patchCalls;
        private String lastOnlineTimePlayerId;
        private String lastPatchPlayerId;
        private ModelsPatchPlayerRequest lastPatchRequest;

        @Override
        public ModelsPlayerOnlineTimeResponse getPlayerOnlineTime(String xNetworkID, String playerId) {
            onlineTimeGetCalls++;
            lastOnlineTimePlayerId = playerId;

            ModelsPlayerOnlineTimeResponse response = new ModelsPlayerOnlineTimeResponse();
            response.setPlayerId(playerId);
            response.setOnlineTimeSeconds(onlineTimeSeconds);
            return response;
        }

        @Override
        public ModelsPlayerResponse patchPlayer(
                String xNetworkID,
                String playerId,
                ModelsPatchPlayerRequest modelsPatchPlayerRequest
        ) {
            patchCalls++;
            lastPatchPlayerId = playerId;
            lastPatchRequest = modelsPatchPlayerRequest;
            if (modelsPatchPlayerRequest.getOnlineTimeSeconds() != null) {
                onlineTimeSeconds = modelsPatchPlayerRequest.getOnlineTimeSeconds();
            }
            return playerResponse(onlineTimeSeconds);
        }
    }
}
