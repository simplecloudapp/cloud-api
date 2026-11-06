package app.simplecloud.api.internal.player;

import app.simplecloud.api.CloudApiException;
import app.simplecloud.api.CloudApiOptions;
import app.simplecloud.api.internal.nats.FakeNatsConnection;
import app.simplecloud.api.player.CloudPlayer;
import app.simplecloud.api.player.ServerSelectionMode;
import app.simplecloud.api.web.ApiException;
import app.simplecloud.api.web.apis.PlayersApi;
import app.simplecloud.api.web.models.ModelsConnectPlayerRequest;
import app.simplecloud.api.web.models.ModelsConnectPlayerResponse;
import app.simplecloud.api.web.models.ModelsConnectResult;
import app.simplecloud.api.web.models.ModelsPatchPlayerRequest;
import app.simplecloud.api.web.models.ModelsPlayerOnlineTimeResponse;
import app.simplecloud.api.web.models.ModelsPlayerResponse;
import build.buf.gen.simplecloud.player.v2.ConnectPlayerRequest;
import build.buf.gen.simplecloud.player.v2.ConnectPlayerResponse;
import build.buf.gen.simplecloud.player.v2.ConnectResult;
import io.nats.client.Message;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlayerApiImplTest {

    private static final UUID PLAYER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String CONNECT_SUBJECT = "network.player." + PLAYER_ID + ".connect";
    private static final String PERSISTENT_SERVER_ID = "22222222-2222-2222-2222-222222222222";

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

        CompletionException negative = assertThrows(
                CompletionException.class,
                () -> api.setOnlineTimeSeconds(PLAYER_ID, -1).join());
        CompletionException tooLarge = assertThrows(
                CompletionException.class,
                () -> api.setOnlineTimeSeconds(PLAYER_ID, (long) Integer.MAX_VALUE + 1L)
                        .join());

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

        CloudPlayer.ConnectResult result =
                api.connectDirect(PLAYER_ID, "lobby-1").join();

        assertEquals(CloudPlayer.ConnectResult.SUCCESS, result);
        assertEquals(List.of(CONNECT_SUBJECT), subjects);
        assertEquals(List.of(expectedConnectRequest("lobby-1")), requests);
    }

    @Test
    void connectDirect_withoutReply_reportsPlayerNotFound() {
        FakeNatsConnection nats = FakeNatsConnection.answering((subject, data, timeout) -> null);
        PlayerApiImpl api = new PlayerApiImpl(options(), nats.connection(), new FakePlayersApi());

        CloudPlayer.ConnectResult result =
                api.connectDirect(PLAYER_ID, "lobby-1").join();

        assertEquals(CloudPlayer.ConnectResult.PLAYER_NOT_FOUND, result);
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

    @Test
    void connectToGroup_sendsTheGroupName() {
        FakePlayersApi playersApi = new FakePlayersApi();
        playersApi.connectResult = ModelsConnectResult.ConnectResultSuccess;
        PlayerApiImpl api = new PlayerApiImpl(options(), null, playersApi);

        CloudPlayer.ConnectResult result =
                api.connectToGroup(PLAYER_ID, "lobby", ServerSelectionMode.MIN_PLAYERS).join();

        assertEquals(CloudPlayer.ConnectResult.SUCCESS, result);
        assertEquals(PLAYER_ID.toString(), playersApi.lastConnectPlayerId);
        assertEquals("lobby", playersApi.lastConnectRequest.getServerGroupName());
        assertNull(playersApi.lastConnectRequest.getServerGroupId());
        assertEquals("MIN_PLAYERS", playersApi.lastConnectRequest.getSelectionMode());
    }

    @Test
    void connectToPersistentServer_sendsThePersistentServerId() {
        FakePlayersApi playersApi = new FakePlayersApi();
        playersApi.connectResult = ModelsConnectResult.ConnectResultSuccess;
        PlayerApiImpl api = new PlayerApiImpl(options(), null, playersApi);

        api.connectToPersistentServer(PLAYER_ID, PERSISTENT_SERVER_ID).join();

        assertEquals(PERSISTENT_SERVER_ID, playersApi.lastConnectRequest.getPersistentServerId());
        assertNull(playersApi.lastConnectRequest.getServerGroupName());
    }

    @ParameterizedTest
    @CsvSource({
        "ConnectResultSuccess, SUCCESS",
        "ConnectResultAlreadyConnected, ALREADY_CONNECTED",
        "ConnectResultNoServerAvailable, NO_SERVER_AVAILABLE",
        "ConnectResultServerNotFound, SERVER_NOT_FOUND",
        "ConnectResultFailed, CONNECTION_FAILED"
    })
    void connectToGroup_mapsTheControllerResult(ModelsConnectResult controllerResult, CloudPlayer.ConnectResult expected) {
        FakePlayersApi playersApi = new FakePlayersApi();
        playersApi.connectResult = controllerResult;
        PlayerApiImpl api = new PlayerApiImpl(options(), null, playersApi);

        assertEquals(expected, api.connectToGroup(PLAYER_ID, "lobby").join());
    }

    @Test
    void connectToGroup_ignoresTheMessageText() {
        FakePlayersApi playersApi = new FakePlayersApi();
        playersApi.connectResult = ModelsConnectResult.ConnectResultNoServerAvailable;
        playersApi.connectMessage = "Target server not found";
        PlayerApiImpl api = new PlayerApiImpl(options(), null, playersApi);

        assertEquals(CloudPlayer.ConnectResult.NO_SERVER_AVAILABLE, api.connectToGroup(PLAYER_ID, "lobby").join());
    }

    @Test
    void connectToGroup_offlinePlayer_reportsPlayerNotFound() {
        FakePlayersApi playersApi = new FakePlayersApi();
        playersApi.connectFailure = new ApiException(404, Map.of(), "{\"error\":\"Player is not online\"}");
        PlayerApiImpl api = new PlayerApiImpl(options(), null, playersApi);

        assertEquals(CloudPlayer.ConnectResult.PLAYER_NOT_FOUND, api.connectToGroup(PLAYER_ID, "lobby").join());
    }

    @Test
    void connectToGroup_unknownGroup_reportsServerNotFound() {
        FakePlayersApi playersApi = new FakePlayersApi();
        playersApi.connectFailure = new ApiException(404, Map.of(), "{\"error\":\"Server group not found\"}");
        PlayerApiImpl api = new PlayerApiImpl(options(), null, playersApi);

        assertEquals(CloudPlayer.ConnectResult.SERVER_NOT_FOUND, api.connectToGroup(PLAYER_ID, "lobby").join());
    }

    @Test
    void connectToPersistentServer_rejectedRequest_failsWithCloudApiException() {
        FakePlayersApi playersApi = new FakePlayersApi();
        playersApi.connectFailure =
                new ApiException(400, Map.of(), "{\"error\":\"persistent_server_id must be a valid UUID\"}");
        PlayerApiImpl api = new PlayerApiImpl(options(), null, playersApi);

        CompletionException failure = assertThrows(
                CompletionException.class, () -> api.connectToPersistentServer(PLAYER_ID, "survival")
                        .join());

        assertInstanceOf(CloudApiException.class, failure.getCause());
    }

    private static ConnectPlayerRequest expectedConnectRequest(String serverName) {
        return ConnectPlayerRequest.newBuilder()
                .setPlayerId(PLAYER_ID.toString())
                .setServerName(serverName)
                .build();
    }

    private static Message connectResponse(ConnectResult result) {
        ConnectPlayerResponse response =
                ConnectPlayerResponse.newBuilder().setResult(result).build();
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
        private ModelsConnectResult connectResult;
        private String connectMessage;
        private ApiException connectFailure;
        private String lastConnectPlayerId;
        private ModelsConnectPlayerRequest lastConnectRequest;

        @Override
        public ModelsConnectPlayerResponse connectPlayer(
                String xNetworkID, String playerId, ModelsConnectPlayerRequest modelsConnectPlayerRequest)
                throws ApiException {
            lastConnectPlayerId = playerId;
            lastConnectRequest = modelsConnectPlayerRequest;
            if (connectFailure != null) {
                throw connectFailure;
            }
            ModelsConnectPlayerResponse response = new ModelsConnectPlayerResponse();
            response.setPlayerId(playerId);
            response.setSuccess(connectResult == ModelsConnectResult.ConnectResultSuccess);
            response.setResult(connectResult);
            response.setMessage(connectMessage);
            return response;
        }

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
                String xNetworkID, String playerId, ModelsPatchPlayerRequest modelsPatchPlayerRequest) {
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
