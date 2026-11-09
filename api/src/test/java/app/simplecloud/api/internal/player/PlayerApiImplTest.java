package app.simplecloud.api.internal.player;

import app.simplecloud.api.CloudApiException;
import app.simplecloud.api.CloudApiOptions;
import app.simplecloud.api.internal.nats.FakeNatsConnection;
import app.simplecloud.api.player.CloudPlayer;
import app.simplecloud.api.group.Group;
import app.simplecloud.api.persistentserver.PersistentServer;
import app.simplecloud.api.player.ConnectOutcome;
import app.simplecloud.api.player.ConnectStatus;
import app.simplecloud.api.player.ConnectTarget;
import app.simplecloud.api.player.ServerSelectionMode;
import app.simplecloud.api.server.Server;
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

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

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
    private static final String PLAYER_OFFLINE_BODY = "{\"error\":\"Player is not online\"}";
    private static final String UNKNOWN_SERVER_BODY = "{\"error\":\"Server not found\"}";

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
    void connectTo_withRegisteredServerName_sendsTheNameToThePlayersProxy() {
        List<String> subjects = new ArrayList<>();
        List<ConnectPlayerRequest> requests = new ArrayList<>();
        FakeNatsConnection nats = FakeNatsConnection.answering((subject, data, timeout) -> {
            subjects.add(subject);
            requests.add(ConnectPlayerRequest.parseFrom(data));
            return connectResponse(ConnectResult.CONNECT_RESULT_SUCCESS);
        });
        FakePlayersApi playersApi = new FakePlayersApi();
        PlayerApiImpl api = new PlayerApiImpl(options(), nats.connection(), playersApi);

        ConnectOutcome outcome = api.connectTo(PLAYER_ID, ConnectTarget.registeredServerName("lobby")).join();

        assertEquals(ConnectOutcome.of(ConnectStatus.SUCCESS, null), outcome);
        assertEquals(List.of(CONNECT_SUBJECT), subjects);
        assertEquals(List.of(expectedConnectRequest("lobby")), requests);
        assertEquals(List.of(), playersApi.connectRequests);
    }

    @ParameterizedTest
    @CsvSource({
            "CONNECT_RESULT_SUCCESS, SUCCESS",
            "CONNECT_RESULT_ALREADY_CONNECTED, ALREADY_CONNECTED",
            "CONNECT_RESULT_SERVER_NOT_FOUND, SERVER_NOT_REGISTERED",
            "CONNECT_RESULT_FAILED, FAILED",
            "CONNECT_RESULT_UNSPECIFIED, FAILED",
    })
    void connectTo_withRegisteredServerName_mapsTheProxyResult(ConnectResult proxyResult, ConnectStatus expected) {
        FakeNatsConnection nats = FakeNatsConnection.answering((subject, data, timeout) -> connectResponse(proxyResult));
        PlayerApiImpl api = new PlayerApiImpl(options(), nats.connection(), new FakePlayersApi());

        ConnectOutcome outcome = api.connectTo(PLAYER_ID, ConnectTarget.registeredServerName("lobby")).join();

        assertEquals(expected, outcome.getStatus());
    }

    @Test
    void connectTo_withRegisteredServerName_withProxyResultThisVersionDoesntKnow_failsAsUnknown() {
        int unknownResult = 99;
        ConnectPlayerResponse response = ConnectPlayerResponse.newBuilder().setResultValue(unknownResult).build();
        FakeNatsConnection nats = FakeNatsConnection.answering(
                (subject, data, timeout) -> FakeNatsConnection.reply(response.toByteArray())
        );
        PlayerApiImpl api = new PlayerApiImpl(options(), nats.connection(), new FakePlayersApi());
        ConnectTarget target = ConnectTarget.registeredServerName("lobby");

        CompletionException error = assertThrows(CompletionException.class, () -> api.connectTo(PLAYER_ID, target).join());

        CloudApiException cause = assertInstanceOf(CloudApiException.class, error.getCause());
        assertEquals(CloudApiException.Reason.UNKNOWN, cause.getReason());
    }

    @Test
    void connectTo_withRegisteredServerName_withoutReply_failsAsUnavailable() {
        FakeNatsConnection nats = FakeNatsConnection.answering((subject, data, timeout) -> null);
        PlayerApiImpl api = new PlayerApiImpl(options(), nats.connection(), new FakePlayersApi());
        ConnectTarget target = ConnectTarget.registeredServerName("lobby");

        CompletionException error = assertThrows(CompletionException.class, () -> api.connectTo(PLAYER_ID, target).join());

        CloudApiException cause = assertInstanceOf(CloudApiException.class, error.getCause());
        assertEquals(CloudApiException.Reason.UNAVAILABLE, cause.getReason());
    }

    @Test
    void connectTo_withRegisteredServerName_withNatsFailure_failsAsUnavailable() {
        FakeNatsConnection nats = FakeNatsConnection.answering((subject, data, timeout) -> {
            throw new IllegalStateException("Connection is Closed");
        });
        PlayerApiImpl api = new PlayerApiImpl(options(), nats.connection(), new FakePlayersApi());
        ConnectTarget target = ConnectTarget.registeredServerName("lobby");

        CompletionException error = assertThrows(CompletionException.class, () -> api.connectTo(PLAYER_ID, target).join());

        CloudApiException cause = assertInstanceOf(CloudApiException.class, error.getCause());
        assertEquals(CloudApiException.Reason.UNAVAILABLE, cause.getReason());
    }

    @Test
    @SuppressWarnings("deprecation") // the released connectDirect must keep its request and its results
    void connectDirect_sendsTheNameToThePlayersProxyAndKeepsItsResults() {
        List<ConnectPlayerRequest> requests = new ArrayList<>();
        FakeNatsConnection nats = FakeNatsConnection.answering((subject, data, timeout) -> {
            requests.add(ConnectPlayerRequest.parseFrom(data));
            return connectResponse(ConnectResult.CONNECT_RESULT_SERVER_NOT_FOUND);
        });
        PlayerApiImpl api = new PlayerApiImpl(options(), nats.connection(), new FakePlayersApi());

        CloudPlayer.ConnectResult result = api.connectDirect(PLAYER_ID, "lobby-1").join();

        assertEquals(CloudPlayer.ConnectResult.SERVER_NOT_FOUND, result);
        assertEquals(List.of(expectedConnectRequest("lobby-1")), requests);
    }

    @Test
    @SuppressWarnings("deprecation") // the released connectDirect never fails its future
    void connectDirect_withoutReply_answersConnectionFailed() {
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
            return connectResponse(ConnectResult.CONNECT_RESULT_ALREADY_CONNECTED);
        });
        FakePlayersApi playersApi = new FakePlayersApi();
        PlayerApiImpl api = new PlayerApiImpl(options(), nats.connection(), playersApi);

        CloudPlayer.ConnectResult result = api.connect(PLAYER_ID, "lobby-2").join();

        assertEquals(CloudPlayer.ConnectResult.ALREADY_CONNECTED, result);
        assertEquals(List.of(CONNECT_SUBJECT), subjects);
        assertEquals(List.of(expectedConnectRequest("lobby-2")), requests);
        assertEquals(List.of(), playersApi.connectRequests);
    }

    @Test
    void connectTo_sendsTheControllerOnlyTheTargetsField() {
        FakePlayersApi playersApi = new FakePlayersApi();
        playersApi.connectResult = ModelsConnectResult.ConnectResultSuccess;
        PlayerApiImpl api = new PlayerApiImpl(options(), null, playersApi);
        List<ConnectTarget> targets = List.of(
                ConnectTarget.serverId(SERVER_ID),
                ConnectTarget.server(stub(Server.class, "getServerId", SERVER_ID)),
                ConnectTarget.serverName("lobby-1"),
                ConnectTarget.groupId(GROUP_ID, ServerSelectionMode.FIRST),
                ConnectTarget.group(stub(Group.class, "getServerGroupId", GROUP_ID)),
                ConnectTarget.groupName("lobby"),
                ConnectTarget.groupName("lobby", ServerSelectionMode.MIN_PLAYERS),
                ConnectTarget.persistentServerId(PERSISTENT_SERVER_ID),
                ConnectTarget.persistentServer(stub(PersistentServer.class, "getPersistentServerId", PERSISTENT_SERVER_ID))
        );

        targets.forEach(target -> api.connectTo(PLAYER_ID, target).join());

        List<ModelsConnectPlayerRequest> expected = List.of(
                connectRequest(request -> request.setServerId(SERVER_ID)),
                connectRequest(request -> request.setServerId(SERVER_ID)),
                connectRequest(request -> request.setServerName("lobby-1")),
                connectRequest(request -> {
                    request.setServerGroupId(GROUP_ID);
                    request.setSelectionMode("FIRST");
                }),
                connectRequest(request -> request.setServerGroupId(GROUP_ID)),
                connectRequest(request -> request.setServerGroupName("lobby")),
                connectRequest(request -> {
                    request.setServerGroupName("lobby");
                    request.setSelectionMode("MIN_PLAYERS");
                }),
                connectRequest(request -> request.setPersistentServerId(PERSISTENT_SERVER_ID)),
                connectRequest(request -> request.setPersistentServerId(PERSISTENT_SERVER_ID))
        );
        assertEquals(expected, playersApi.connectRequests);
        assertEquals(PLAYER_ID.toString(), playersApi.lastConnectPlayerId);
    }

    @Test
    void connectTo_returnsTheServerTheControllerTried() {
        FakePlayersApi playersApi = new FakePlayersApi();
        playersApi.connectResult = ModelsConnectResult.ConnectResultSuccess;
        playersApi.connectServerName = "lobby-3";
        PlayerApiImpl api = new PlayerApiImpl(options(), null, playersApi);

        ConnectOutcome outcome = api.connectTo(PLAYER_ID, ConnectTarget.groupName("lobby")).join();

        assertEquals(ConnectOutcome.of(ConnectStatus.SUCCESS, "lobby-3"), outcome);
    }

    @ParameterizedTest
    @CsvSource({
            "SUCCESS, SUCCESS",
            "ALREADY_CONNECTED, ALREADY_CONNECTED",
            "NO_SERVER_AVAILABLE, NO_SERVER_AVAILABLE",
            "SERVER_NOT_FOUND, SERVER_NOT_REGISTERED",
            "FAILED, FAILED",
    })
    void connectTo_mapsTheControllerResult(String controllerResult, ConnectStatus expected) {
        FakePlayersApi playersApi = new FakePlayersApi();
        playersApi.connectResult = ModelsConnectResult.fromValue(controllerResult);
        PlayerApiImpl api = new PlayerApiImpl(options(), null, playersApi);

        ConnectOutcome outcome = api.connectTo(PLAYER_ID, ConnectTarget.serverName("lobby-1")).join();

        assertEquals(expected, outcome.getStatus());
    }

    @Test
    void connectTo_withoutControllerResult_failsAsUnknown() {
        PlayerApiImpl api = new PlayerApiImpl(options(), null, new FakePlayersApi());
        ConnectTarget target = ConnectTarget.serverName("lobby-1");

        CompletionException error = assertThrows(CompletionException.class, () -> api.connectTo(PLAYER_ID, target).join());

        CloudApiException cause = assertInstanceOf(CloudApiException.class, error.getCause());
        assertEquals(CloudApiException.Reason.UNKNOWN, cause.getReason());
    }

    @Test
    void connectTo_withControllerResultThisVersionDoesntKnow_failsAsUnknown() {
        FakePlayersApi playersApi = new FakePlayersApi();
        playersApi.connectReadError = new IllegalArgumentException("Unexpected value 'CANCELLED'");
        PlayerApiImpl api = new PlayerApiImpl(options(), null, playersApi);
        ConnectTarget target = ConnectTarget.serverName("lobby-1");

        CompletionException error = assertThrows(CompletionException.class, () -> api.connectTo(PLAYER_ID, target).join());

        CloudApiException cause = assertInstanceOf(CloudApiException.class, error.getCause());
        assertEquals(CloudApiException.Reason.UNKNOWN, cause.getReason());
    }

    @Test
    void connectTo_withPlayerOffline_answersPlayerNotOnline() {
        FakePlayersApi playersApi = new FakePlayersApi();
        playersApi.connectError = new ApiException(404, "Not Found", null, PLAYER_OFFLINE_BODY);
        PlayerApiImpl api = new PlayerApiImpl(options(), null, playersApi);

        ConnectOutcome outcome = api.connectTo(PLAYER_ID, ConnectTarget.serverName("lobby-1")).join();

        assertEquals(ConnectOutcome.of(ConnectStatus.PLAYER_NOT_ONLINE, null), outcome);
    }

    @Test
    void connectTo_withUnknownTarget_answersTargetNotFound() {
        FakePlayersApi playersApi = new FakePlayersApi();
        playersApi.connectError = new ApiException(404, "Not Found", null, UNKNOWN_SERVER_BODY);
        PlayerApiImpl api = new PlayerApiImpl(options(), null, playersApi);

        ConnectOutcome outcome = api.connectTo(PLAYER_ID, ConnectTarget.serverName("lobby-1")).join();

        assertEquals(ConnectOutcome.of(ConnectStatus.TARGET_NOT_FOUND, null), outcome);
    }

    @ParameterizedTest
    @CsvSource({
            "400, INVALID_REQUEST",
            "409, CONFLICT",
            "500, UNKNOWN",
    })
    void connectTo_withControllerError_fails(int statusCode, CloudApiException.Reason expected) {
        FakePlayersApi playersApi = new FakePlayersApi();
        playersApi.connectError = new ApiException(statusCode, "Error", null, "{\"error\":\"failed\"}");
        PlayerApiImpl api = new PlayerApiImpl(options(), null, playersApi);
        ConnectTarget target = ConnectTarget.serverId(SERVER_ID);

        CompletionException error = assertThrows(CompletionException.class, () -> api.connectTo(PLAYER_ID, target).join());

        CloudApiException cause = assertInstanceOf(CloudApiException.class, error.getCause());
        assertEquals(expected, cause.getReason());
    }

    @Test
    @SuppressWarnings("deprecation") // the released connectToGroup must send the group name and a selection mode
    void connectToGroup_sendsTheGroupNameAndSelectionMode() {
        FakePlayersApi playersApi = new FakePlayersApi();
        playersApi.connectResult = ModelsConnectResult.ConnectResultSuccess;
        PlayerApiImpl api = new PlayerApiImpl(options(), null, playersApi);

        api.connectToGroup(PLAYER_ID, "lobby", ServerSelectionMode.MIN_PLAYERS).join();
        api.connectToGroup(PLAYER_ID, "lobby", null).join();
        api.connectToGroup(PLAYER_ID, "lobby").join();

        List<ModelsConnectPlayerRequest> expected = List.of(
                groupNameRequest("lobby", "MIN_PLAYERS"),
                groupNameRequest("lobby", "RANDOM"),
                groupNameRequest("lobby", "RANDOM")
        );
        assertEquals(expected, playersApi.connectRequests);
    }

    @Test
    @SuppressWarnings("deprecation") // the released connectToPersistentServer must keep sending the ID
    void connectToPersistentServer_sendsOnlyThePersistentServerId() {
        FakePlayersApi playersApi = new FakePlayersApi();
        playersApi.connectResult = ModelsConnectResult.ConnectResultSuccess;
        PlayerApiImpl api = new PlayerApiImpl(options(), null, playersApi);

        api.connectToPersistentServer(PLAYER_ID, PERSISTENT_SERVER_ID).join();

        ModelsConnectPlayerRequest expected = connectRequest(request -> request.setPersistentServerId(PERSISTENT_SERVER_ID));
        assertEquals(List.of(expected), playersApi.connectRequests);
    }

    @ParameterizedTest
    @CsvSource({
            "SUCCESS, , SUCCESS",
            "ALREADY_CONNECTED, , ALREADY_CONNECTED",
            "NO_SERVER_AVAILABLE, , SERVER_NOT_FOUND",
            "SERVER_NOT_FOUND, , SERVER_NOT_FOUND",
            "FAILED, , CONNECTION_FAILED",
            ", player offline, PLAYER_NOT_FOUND",
            ", unknown target, SERVER_NOT_FOUND",
    })
    @SuppressWarnings("deprecation") // plugins built against the released methods must only get the released results
    void deprecatedControllerConnects_answerOnlyTheReleasedResults(
            String controllerResult,
            String notFound,
            CloudPlayer.ConnectResult expected
    ) {
        FakePlayersApi playersApi = new FakePlayersApi();
        if (controllerResult != null) {
            playersApi.connectResult = ModelsConnectResult.fromValue(controllerResult);
        } else {
            String body = notFound.equals("player offline") ? PLAYER_OFFLINE_BODY : UNKNOWN_SERVER_BODY;
            playersApi.connectError = new ApiException(404, "Not Found", null, body);
        }
        PlayerApiImpl api = new PlayerApiImpl(options(), null, playersApi);

        CloudPlayer.ConnectResult groupResult = api.connectToGroup(PLAYER_ID, "lobby").join();
        CloudPlayer.ConnectResult persistentResult = api.connectToPersistentServer(PLAYER_ID, PERSISTENT_SERVER_ID).join();

        assertEquals(expected, groupResult);
        assertEquals(expected, persistentResult);
    }

    @Test
    @SuppressWarnings("deprecation") // the released CloudPlayer methods must forward to the same requests
    void cloudPlayer_sendsEachConnectMethodsRequest() {
        List<ConnectPlayerRequest> proxyRequests = new ArrayList<>();
        FakeNatsConnection nats = FakeNatsConnection.answering((subject, data, timeout) -> {
            proxyRequests.add(ConnectPlayerRequest.parseFrom(data));
            return connectResponse(ConnectResult.CONNECT_RESULT_SUCCESS);
        });
        FakePlayersApi playersApi = new FakePlayersApi();
        playersApi.connectResult = ModelsConnectResult.ConnectResultSuccess;
        PlayerApiImpl api = new PlayerApiImpl(options(), nats.connection(), playersApi);
        CloudPlayer player = cloudPlayer(api);

        player.connectTo(ConnectTarget.groupId(GROUP_ID, ServerSelectionMode.FIRST)).join();
        player.connectTo(ConnectTarget.registeredServerName("lobby")).join();
        player.connectDirect("lobby-1").join();
        player.connect("lobby-2").join();
        player.connectToGroup("lobby", ServerSelectionMode.MAX_PLAYERS).join();
        player.connectToGroup("lobby").join();
        player.connectToPersistentServer(PERSISTENT_SERVER_ID).join();

        List<ModelsConnectPlayerRequest> expectedControllerRequests = List.of(
                connectRequest(request -> {
                    request.setServerGroupId(GROUP_ID);
                    request.setSelectionMode("FIRST");
                }),
                groupNameRequest("lobby", "MAX_PLAYERS"),
                groupNameRequest("lobby", "RANDOM"),
                connectRequest(request -> request.setPersistentServerId(PERSISTENT_SERVER_ID))
        );
        List<ConnectPlayerRequest> expectedProxyRequests = List.of(
                expectedConnectRequest("lobby"),
                expectedConnectRequest("lobby-1"),
                expectedConnectRequest("lobby-2")
        );
        assertEquals(expectedControllerRequests, playersApi.connectRequests);
        assertEquals(expectedProxyRequests, proxyRequests);
    }

    private static ModelsConnectPlayerRequest connectRequest(Consumer<ModelsConnectPlayerRequest> fields) {
        ModelsConnectPlayerRequest request = new ModelsConnectPlayerRequest();
        fields.accept(request);
        return request;
    }

    private static ModelsConnectPlayerRequest groupNameRequest(String groupName, String selectionMode) {
        return connectRequest(request -> {
            request.setServerGroupName(groupName);
            request.setSelectionMode(selectionMode);
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> T stub(Class<T> type, String getter, String value) {
        return (T) Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[]{type},
                (proxy, method, args) -> {
                    if (method.getName().equals(getter)) {
                        return value;
                    }
                    throw new UnsupportedOperationException(method.getName());
                }
        );
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
        private IllegalArgumentException connectReadError;
        private String lastConnectPlayerId;
        private final List<ModelsConnectPlayerRequest> connectRequests = new ArrayList<>();

        @Override
        public ModelsConnectPlayerResponse connectPlayer(
                String xNetworkID,
                String playerId,
                ModelsConnectPlayerRequest modelsConnectPlayerRequest
        ) throws ApiException {
            lastConnectPlayerId = playerId;
            connectRequests.add(modelsConnectPlayerRequest);
            if (connectError != null) {
                throw connectError;
            }
            if (connectReadError != null) {
                throw connectReadError;
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
