package app.simplecloud.api.internal.player;

import app.simplecloud.api.CloudApiException;
import app.simplecloud.api.CloudApiOptions;
import app.simplecloud.api.internal.nats.FakeNatsConnection;
import app.simplecloud.api.player.CloudPlayer;
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
import java.util.UUID;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlayerApiImplTest {

    private static final UUID PLAYER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String SERVER_ID = "22222222-2222-2222-2222-222222222222";
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
    void connect_sendsOnlyTheCloudServerNameToTheController() {
        FakePlayersApi playersApi = new FakePlayersApi();
        playersApi.connectResult = ModelsConnectResult.ConnectResultSuccess;
        PlayerApiImpl api = new PlayerApiImpl(options(), null, playersApi);

        CloudPlayer.ConnectResult result = api.connect(PLAYER_ID, "lobby-2").join();

        assertEquals(CloudPlayer.ConnectResult.SUCCESS, result);
        assertEquals(PLAYER_ID.toString(), playersApi.lastConnectPlayerId);
        ModelsConnectPlayerRequest expected = new ModelsConnectPlayerRequest();
        expected.setServerName("lobby-2");
        assertEquals(expected, playersApi.lastConnectRequest);
    }

    @Test
    void connectByServerId_sendsOnlyTheServerIdToTheController() {
        FakePlayersApi playersApi = new FakePlayersApi();
        playersApi.connectResult = ModelsConnectResult.ConnectResultSuccess;
        PlayerApiImpl api = new PlayerApiImpl(options(), null, playersApi);

        api.connectByServerId(PLAYER_ID, SERVER_ID).join();

        ModelsConnectPlayerRequest expected = new ModelsConnectPlayerRequest();
        expected.setServerId(SERVER_ID);
        assertEquals(expected, playersApi.lastConnectRequest);
    }

    @Test
    void connectToGroup_sendsTheGroupNameAndRandomWithoutSelectionMode() {
        FakePlayersApi playersApi = new FakePlayersApi();
        playersApi.connectResult = ModelsConnectResult.ConnectResultSuccess;
        PlayerApiImpl api = new PlayerApiImpl(options(), null, playersApi);

        api.connectToGroup(PLAYER_ID, "lobby", null).join();

        ModelsConnectPlayerRequest expected = new ModelsConnectPlayerRequest();
        expected.setServerGroupName("lobby");
        expected.setSelectionMode("RANDOM");
        assertEquals(expected, playersApi.lastConnectRequest);
    }

    @ParameterizedTest
    @CsvSource({
            "SUCCESS, SUCCESS",
            "ALREADY_CONNECTED, ALREADY_CONNECTED",
            "NO_SERVER_AVAILABLE, NO_SERVER_AVAILABLE",
            "SERVER_NOT_FOUND, SERVER_NOT_FOUND",
            "FAILED, CONNECTION_FAILED",
    })
    void connect_mapsTheControllerResult(String controllerResult, CloudPlayer.ConnectResult expected) {
        FakePlayersApi playersApi = new FakePlayersApi();
        playersApi.connectResult = ModelsConnectResult.fromValue(controllerResult);
        PlayerApiImpl api = new PlayerApiImpl(options(), null, playersApi);

        assertEquals(expected, api.connect(PLAYER_ID, "lobby-1").join());
    }

    @Test
    void connect_withoutControllerResult_failsConnection() {
        FakePlayersApi playersApi = new FakePlayersApi();
        PlayerApiImpl api = new PlayerApiImpl(options(), null, playersApi);

        assertEquals(CloudPlayer.ConnectResult.CONNECTION_FAILED, api.connect(PLAYER_ID, "lobby-1").join());
    }

    @Test
    void connect_mapsNotFoundAnswers() {
        FakePlayersApi offline = new FakePlayersApi();
        offline.connectError = new ApiException(404, "Not Found", null, "{\"error\":\"Player is not online\"}");
        FakePlayersApi unknownServer = new FakePlayersApi();
        unknownServer.connectError = new ApiException(404, "Not Found", null, "{\"error\":\"Server not found\"}");

        CloudPlayer.ConnectResult offlineResult = new PlayerApiImpl(options(), null, offline)
                .connect(PLAYER_ID, "lobby-1").join();
        CloudPlayer.ConnectResult unknownServerResult = new PlayerApiImpl(options(), null, unknownServer)
                .connect(PLAYER_ID, "lobby-1").join();

        assertEquals(CloudPlayer.ConnectResult.PLAYER_NOT_FOUND, offlineResult);
        assertEquals(CloudPlayer.ConnectResult.SERVER_NOT_FOUND, unknownServerResult);
    }

    @Test
    void connect_withAmbiguousName_completesWithConflict() {
        FakePlayersApi playersApi = new FakePlayersApi();
        playersApi.connectError = new ApiException(409, "Conflict", null, "{\"error\":\"Ambiguous server name\"}");
        PlayerApiImpl api = new PlayerApiImpl(options(), null, playersApi);

        CompletionException error = assertThrows(CompletionException.class, () -> api.connect(PLAYER_ID, "lobby-1").join());

        CloudApiException cause = assertInstanceOf(CloudApiException.class, error.getCause());
        assertEquals(CloudApiException.Reason.CONFLICT, cause.getReason());
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
        private ModelsConnectResult connectResult;
        private ApiException connectError;
        private String lastConnectPlayerId;
        private ModelsConnectPlayerRequest lastConnectRequest;

        @Override
        public ModelsConnectPlayerResponse connectPlayer(
                String xNetworkID,
                String playerId,
                ModelsConnectPlayerRequest modelsConnectPlayerRequest
        ) throws ApiException {
            lastConnectPlayerId = playerId;
            lastConnectRequest = modelsConnectPlayerRequest;
            if (connectError != null) {
                throw connectError;
            }
            ModelsConnectPlayerResponse response = new ModelsConnectPlayerResponse();
            response.setResult(connectResult);
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
