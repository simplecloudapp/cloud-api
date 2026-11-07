package app.simplecloud.api.internal.player;

import app.simplecloud.api.CloudApiException;
import app.simplecloud.api.CloudApiOptions;
import app.simplecloud.api.internal.nats.FakeNatsConnection;
import app.simplecloud.api.player.CloudPlayer;
import app.simplecloud.api.player.ConnectResponse;
import app.simplecloud.api.player.ConnectTarget;
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
import java.util.UUID;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlayerApiImplTest {

    private static final UUID PLAYER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String SERVER_ID = "22222222-2222-2222-2222-222222222222";
    private static final String GROUP_ID = "33333333-3333-3333-3333-333333333333";
    private static final String PERSISTENT_SERVER_ID = "44444444-4444-4444-4444-444444444444";
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
    @SuppressWarnings("deprecation") // the released connect must keep sending the proxy's name to the proxy
    void connect_sendsTheSameRequestAsConnectDirect() {
        List<String> subjects = new ArrayList<>();
        List<ConnectPlayerRequest> requests = new ArrayList<>();
        FakeNatsConnection nats = FakeNatsConnection.answering((subject, data, timeout) -> {
            subjects.add(subject);
            requests.add(ConnectPlayerRequest.parseFrom(data));
            return connectResponse(ConnectResult.CONNECT_RESULT_SERVER_NOT_FOUND);
        });
        FakePlayersApi playersApi = new FakePlayersApi();
        PlayerApiImpl api = new PlayerApiImpl(options(), nats.connection(), playersApi);

        CloudPlayer.ConnectResult result = api.connect(PLAYER_ID, "lobby-2").join();

        assertEquals(CloudPlayer.ConnectResult.SERVER_NOT_FOUND, result);
        assertEquals(List.of(CONNECT_SUBJECT), subjects);
        assertEquals(List.of(expectedConnectRequest("lobby-2")), requests);
        assertNull(playersApi.lastConnectRequest);
    }

    @Test
    void connectToServerByName_sendsOnlyTheCloudServerNameToTheController() {
        FakePlayersApi playersApi = new FakePlayersApi();
        playersApi.connectResult = ModelsConnectResult.ConnectResultSuccess;
        PlayerApiImpl api = new PlayerApiImpl(options(), null, playersApi);

        CloudPlayer.ConnectResult result = api.connectToServerByName(PLAYER_ID, "lobby-2").join();

        assertEquals(CloudPlayer.ConnectResult.SUCCESS, result);
        assertEquals(PLAYER_ID.toString(), playersApi.lastConnectPlayerId);
        ModelsConnectPlayerRequest expected = new ModelsConnectPlayerRequest();
        expected.setServerName("lobby-2");
        assertEquals(expected, playersApi.lastConnectRequest);
    }

    @Test
    void connectToServerById_sendsOnlyTheServerIdToTheController() {
        FakePlayersApi playersApi = new FakePlayersApi();
        playersApi.connectResult = ModelsConnectResult.ConnectResultSuccess;
        PlayerApiImpl api = new PlayerApiImpl(options(), null, playersApi);

        api.connectToServerById(PLAYER_ID, SERVER_ID).join();

        ModelsConnectPlayerRequest expected = new ModelsConnectPlayerRequest();
        expected.setServerId(SERVER_ID);
        assertEquals(expected, playersApi.lastConnectRequest);
    }

    @Test
    void connectToGroupByName_sendsTheGroupNameAndRandomWithoutSelectionMode() {
        FakePlayersApi playersApi = new FakePlayersApi();
        playersApi.connectResult = ModelsConnectResult.ConnectResultSuccess;
        PlayerApiImpl api = new PlayerApiImpl(options(), null, playersApi);

        api.connectToGroupByName(PLAYER_ID, "lobby", null).join();

        ModelsConnectPlayerRequest expected = new ModelsConnectPlayerRequest();
        expected.setServerGroupName("lobby");
        expected.setSelectionMode("RANDOM");
        assertEquals(expected, playersApi.lastConnectRequest);
    }

    @Test
    @SuppressWarnings("deprecation") // the released connectToGroup must send the group name in the name field
    void connectToGroup_sendsTheGroupNameAndSelectionMode() {
        FakePlayersApi playersApi = new FakePlayersApi();
        playersApi.connectResult = ModelsConnectResult.ConnectResultSuccess;
        PlayerApiImpl api = new PlayerApiImpl(options(), null, playersApi);

        api.connectToGroup(PLAYER_ID, "lobby", ServerSelectionMode.MIN_PLAYERS).join();

        ModelsConnectPlayerRequest expected = new ModelsConnectPlayerRequest();
        expected.setServerGroupName("lobby");
        expected.setSelectionMode("MIN_PLAYERS");
        assertEquals(expected, playersApi.lastConnectRequest);
    }

    @Test
    void connectTo_withGroupId_sendsTheGroupIdAndSelectionMode() {
        FakePlayersApi playersApi = new FakePlayersApi();
        playersApi.connectResult = ModelsConnectResult.ConnectResultSuccess;
        PlayerApiImpl api = new PlayerApiImpl(options(), null, playersApi);
        ConnectTarget target = new ConnectTarget.GroupId(GROUP_ID, ServerSelectionMode.FIRST);

        api.connectTo(PLAYER_ID, target).join();

        ModelsConnectPlayerRequest expected = new ModelsConnectPlayerRequest();
        expected.setServerGroupId(GROUP_ID);
        expected.setSelectionMode("FIRST");
        assertEquals(expected, playersApi.lastConnectRequest);
    }

    @Test
    @SuppressWarnings("deprecation") // the released connectToPersistentServer must keep sending the ID
    void connectToPersistentServer_sendsOnlyThePersistentServerId() {
        FakePlayersApi playersApi = new FakePlayersApi();
        playersApi.connectResult = ModelsConnectResult.ConnectResultSuccess;
        PlayerApiImpl api = new PlayerApiImpl(options(), null, playersApi);

        api.connectToPersistentServer(PLAYER_ID, PERSISTENT_SERVER_ID).join();

        ModelsConnectPlayerRequest expected = new ModelsConnectPlayerRequest();
        expected.setPersistentServerId(PERSISTENT_SERVER_ID);
        assertEquals(expected, playersApi.lastConnectRequest);
    }

    @Test
    void connectTo_returnsTheServerTheControllerTried() {
        FakePlayersApi playersApi = new FakePlayersApi();
        playersApi.connectResult = ModelsConnectResult.ConnectResultSuccess;
        playersApi.connectServerName = "lobby-3";
        PlayerApiImpl api = new PlayerApiImpl(options(), null, playersApi);
        ConnectTarget target = new ConnectTarget.GroupName("lobby", null);

        ConnectResponse response = api.connectTo(PLAYER_ID, target).join();

        assertEquals(new ConnectResponse(CloudPlayer.ConnectResult.SUCCESS, "lobby-3"), response);
    }

    @ParameterizedTest
    @CsvSource({
            "SUCCESS, SUCCESS",
            "ALREADY_CONNECTED, ALREADY_CONNECTED",
            "NO_SERVER_AVAILABLE, NO_SERVER_AVAILABLE",
            "SERVER_NOT_FOUND, SERVER_NOT_FOUND",
            "FAILED, CONNECTION_FAILED",
    })
    void connectToServerByName_mapsTheControllerResult(String controllerResult, CloudPlayer.ConnectResult expected) {
        FakePlayersApi playersApi = new FakePlayersApi();
        playersApi.connectResult = ModelsConnectResult.fromValue(controllerResult);
        PlayerApiImpl api = new PlayerApiImpl(options(), null, playersApi);

        CloudPlayer.ConnectResult result = api.connectToServerByName(PLAYER_ID, "lobby-1").join();

        assertEquals(expected, result);
    }

    @Test
    void connectToServerByName_withoutControllerResult_failsConnection() {
        FakePlayersApi playersApi = new FakePlayersApi();
        PlayerApiImpl api = new PlayerApiImpl(options(), null, playersApi);

        CloudPlayer.ConnectResult result = api.connectToServerByName(PLAYER_ID, "lobby-1").join();

        assertEquals(CloudPlayer.ConnectResult.CONNECTION_FAILED, result);
    }

    @Test
    void connectTo_withPlayerOffline_answersPlayerNotFoundWithoutServer() {
        FakePlayersApi playersApi = new FakePlayersApi();
        String playerOfflineBody = "{\"error\":\"Player is not online\"}";
        playersApi.connectError = new ApiException(404, "Not Found", null, playerOfflineBody);
        PlayerApiImpl api = new PlayerApiImpl(options(), null, playersApi);
        ConnectTarget target = new ConnectTarget.ServerName("lobby-1");

        ConnectResponse response = api.connectTo(PLAYER_ID, target).join();

        assertEquals(new ConnectResponse(CloudPlayer.ConnectResult.PLAYER_NOT_FOUND, null), response);
    }

    @Test
    void connectToServerByName_withUnknownServer_answersServerNotFound() {
        FakePlayersApi playersApi = new FakePlayersApi();
        String unknownServerBody = "{\"error\":\"Server not found\"}";
        playersApi.connectError = new ApiException(404, "Not Found", null, unknownServerBody);
        PlayerApiImpl api = new PlayerApiImpl(options(), null, playersApi);

        CloudPlayer.ConnectResult result = api.connectToServerByName(PLAYER_ID, "lobby-1").join();

        assertEquals(CloudPlayer.ConnectResult.SERVER_NOT_FOUND, result);
    }

    @Test
    void connectToServerByName_withAmbiguousName_completesWithConflict() {
        FakePlayersApi playersApi = new FakePlayersApi();
        String ambiguousNameBody = "{\"error\":\"Ambiguous server name\"}";
        playersApi.connectError = new ApiException(409, "Conflict", null, ambiguousNameBody);
        PlayerApiImpl api = new PlayerApiImpl(options(), null, playersApi);

        CompletionException error = assertThrows(CompletionException.class, () -> api.connectToServerByName(PLAYER_ID, "lobby-1").join());

        CloudApiException cause = assertInstanceOf(CloudApiException.class, error.getCause());
        assertEquals(CloudApiException.Reason.CONFLICT, cause.getReason());
    }

    @Test
    void connectToServerById_withInvalidId_completesWithInvalidRequest() {
        FakePlayersApi playersApi = new FakePlayersApi();
        String invalidIdBody = "{\"error\":\"server_id must be a valid UUID\"}";
        playersApi.connectError = new ApiException(400, "Bad Request", null, invalidIdBody);
        PlayerApiImpl api = new PlayerApiImpl(options(), null, playersApi);

        CompletionException error = assertThrows(CompletionException.class, () -> api.connectToServerById(PLAYER_ID, "lobby-1").join());

        CloudApiException cause = assertInstanceOf(CloudApiException.class, error.getCause());
        assertEquals(CloudApiException.Reason.INVALID_REQUEST, cause.getReason());
    }

    @Test
    @SuppressWarnings("deprecation") // plugins built before NO_SERVER_AVAILABLE existed must never get it
    void deprecatedControllerConnects_answerServerNotFoundInsteadOfNoServerAvailable() {
        FakePlayersApi playersApi = new FakePlayersApi();
        playersApi.connectResult = ModelsConnectResult.ConnectResultNoServerAvailable;
        PlayerApiImpl api = new PlayerApiImpl(options(), null, playersApi);

        CloudPlayer.ConnectResult groupResult = api.connectToGroup(PLAYER_ID, "lobby").join();
        CloudPlayer.ConnectResult persistentResult = api.connectToPersistentServer(PLAYER_ID, PERSISTENT_SERVER_ID).join();
        CloudPlayer.ConnectResult newGroupResult = api.connectToGroupByName(PLAYER_ID, "lobby").join();

        assertEquals(CloudPlayer.ConnectResult.SERVER_NOT_FOUND, groupResult);
        assertEquals(CloudPlayer.ConnectResult.SERVER_NOT_FOUND, persistentResult);
        assertEquals(CloudPlayer.ConnectResult.NO_SERVER_AVAILABLE, newGroupResult);
    }

    @Test
    @SuppressWarnings("deprecation") // the released CloudPlayer.connect must keep going straight to the proxy
    void cloudPlayerConnect_sendsTheProxyNameToTheProxy() {
        List<ConnectPlayerRequest> requests = new ArrayList<>();
        FakeNatsConnection nats = FakeNatsConnection.answering((subject, data, timeout) -> {
            requests.add(ConnectPlayerRequest.parseFrom(data));
            return connectResponse(ConnectResult.CONNECT_RESULT_SUCCESS);
        });
        FakePlayersApi playersApi = new FakePlayersApi();
        PlayerApiImpl api = new PlayerApiImpl(options(), nats.connection(), playersApi);
        CloudPlayer player = cloudPlayer(api);

        CloudPlayer.ConnectResult result = player.connect("lobby-1").join();

        assertEquals(CloudPlayer.ConnectResult.SUCCESS, result);
        assertEquals(List.of(expectedConnectRequest("lobby-1")), requests);
        assertNull(playersApi.lastConnectRequest);
    }

    @Test
    @SuppressWarnings("deprecation") // the released CloudPlayer connects through the controller keep their results
    void cloudPlayerDeprecatedControllerConnects_sendTheirTargetAndAnswerServerNotFound() {
        FakePlayersApi playersApi = new FakePlayersApi();
        playersApi.connectResult = ModelsConnectResult.ConnectResultNoServerAvailable;
        PlayerApiImpl api = new PlayerApiImpl(options(), null, playersApi);
        CloudPlayer player = cloudPlayer(api);

        CloudPlayer.ConnectResult groupResult = player.connectToGroup("lobby").join();
        CloudPlayer.ConnectResult groupWithModeResult = player.connectToGroup("lobby", ServerSelectionMode.MAX_PLAYERS).join();
        CloudPlayer.ConnectResult persistentResult = player.connectToPersistentServer(PERSISTENT_SERVER_ID).join();

        ModelsConnectPlayerRequest groupRequest = new ModelsConnectPlayerRequest();
        groupRequest.setServerGroupName("lobby");
        groupRequest.setSelectionMode("RANDOM");
        ModelsConnectPlayerRequest groupWithModeRequest = new ModelsConnectPlayerRequest();
        groupWithModeRequest.setServerGroupName("lobby");
        groupWithModeRequest.setSelectionMode("MAX_PLAYERS");
        ModelsConnectPlayerRequest persistentRequest = new ModelsConnectPlayerRequest();
        persistentRequest.setPersistentServerId(PERSISTENT_SERVER_ID);
        assertEquals(List.of(groupRequest, groupWithModeRequest, persistentRequest), playersApi.connectRequests);
        assertEquals(CloudPlayer.ConnectResult.SERVER_NOT_FOUND, groupResult);
        assertEquals(CloudPlayer.ConnectResult.SERVER_NOT_FOUND, groupWithModeResult);
        assertEquals(CloudPlayer.ConnectResult.SERVER_NOT_FOUND, persistentResult);
    }

    @Test
    void cloudPlayerControllerConnects_sendTheTargetTheirNameSays() {
        FakePlayersApi playersApi = new FakePlayersApi();
        playersApi.connectResult = ModelsConnectResult.ConnectResultSuccess;
        PlayerApiImpl api = new PlayerApiImpl(options(), null, playersApi);
        CloudPlayer player = cloudPlayer(api);
        ConnectTarget groupIdTarget = new ConnectTarget.GroupId(GROUP_ID, ServerSelectionMode.FIRST);

        player.connectTo(groupIdTarget).join();
        player.connectToServerByName("lobby-1").join();
        player.connectToServerById(SERVER_ID).join();
        player.connectToGroupByName("lobby", ServerSelectionMode.MIN_PLAYERS).join();
        player.connectToGroupByName("lobby").join();
        player.connectToPersistentServerById(PERSISTENT_SERVER_ID).join();

        ModelsConnectPlayerRequest byGroupId = new ModelsConnectPlayerRequest();
        byGroupId.setServerGroupId(GROUP_ID);
        byGroupId.setSelectionMode("FIRST");
        ModelsConnectPlayerRequest byServerName = new ModelsConnectPlayerRequest();
        byServerName.setServerName("lobby-1");
        ModelsConnectPlayerRequest byServerId = new ModelsConnectPlayerRequest();
        byServerId.setServerId(SERVER_ID);
        ModelsConnectPlayerRequest byGroupNameWithMode = new ModelsConnectPlayerRequest();
        byGroupNameWithMode.setServerGroupName("lobby");
        byGroupNameWithMode.setSelectionMode("MIN_PLAYERS");
        ModelsConnectPlayerRequest byGroupName = new ModelsConnectPlayerRequest();
        byGroupName.setServerGroupName("lobby");
        byGroupName.setSelectionMode("RANDOM");
        ModelsConnectPlayerRequest byPersistentServerId = new ModelsConnectPlayerRequest();
        byPersistentServerId.setPersistentServerId(PERSISTENT_SERVER_ID);
        List<ModelsConnectPlayerRequest> expected = List.of(
                byGroupId, byServerName, byServerId, byGroupNameWithMode, byGroupName, byPersistentServerId
        );
        assertEquals(expected, playersApi.connectRequests);
    }

    @Test
    void connectToPersistentServerById_sendsOnlyThePersistentServerId() {
        FakePlayersApi playersApi = new FakePlayersApi();
        playersApi.connectResult = ModelsConnectResult.ConnectResultSuccess;
        PlayerApiImpl api = new PlayerApiImpl(options(), null, playersApi);

        api.connectToPersistentServerById(PLAYER_ID, PERSISTENT_SERVER_ID).join();

        ModelsConnectPlayerRequest expected = new ModelsConnectPlayerRequest();
        expected.setPersistentServerId(PERSISTENT_SERVER_ID);
        assertEquals(expected, playersApi.lastConnectRequest);
    }

    private static CloudPlayer cloudPlayer(PlayerApiImpl api) {
        return new CloudPlayerImpl(
                api, null, "network", PLAYER_ID, "Philipp", "Philipp", "proxy-1", "lobby-1",
                true, 0, null, null, null, null
        );
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
        private String connectServerName;
        private ApiException connectError;
        private String lastConnectPlayerId;
        private ModelsConnectPlayerRequest lastConnectRequest;
        private final List<ModelsConnectPlayerRequest> connectRequests = new ArrayList<>();

        @Override
        public ModelsConnectPlayerResponse connectPlayer(
                String xNetworkID,
                String playerId,
                ModelsConnectPlayerRequest modelsConnectPlayerRequest
        ) throws ApiException {
            lastConnectPlayerId = playerId;
            lastConnectRequest = modelsConnectPlayerRequest;
            connectRequests.add(modelsConnectPlayerRequest);
            if (connectError != null) {
                throw connectError;
            }
            ModelsConnectPlayerResponse response = new ModelsConnectPlayerResponse();
            response.setResult(connectResult);
            response.setServerName(connectServerName);
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
