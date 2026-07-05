package app.simplecloud.api.internal.player;

import app.simplecloud.api.CloudApiOptions;
import app.simplecloud.api.player.CloudPlayer;
import app.simplecloud.api.web.apis.PlayersApi;
import app.simplecloud.api.web.models.ModelsPatchPlayerRequest;
import app.simplecloud.api.web.models.ModelsPlayerOnlineTimeResponse;
import app.simplecloud.api.web.models.ModelsPlayerResponse;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlayerApiImplTest {

    private static final UUID PLAYER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

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

        assertThrows(IllegalArgumentException.class, () -> api.setOnlineTimeSeconds(PLAYER_ID, -1));
        assertThrows(IllegalArgumentException.class, () -> api.setOnlineTimeSeconds(PLAYER_ID, (long) Integer.MAX_VALUE + 1L));
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
        public ModelsPlayerOnlineTimeResponse v0PlayersOnlineTimeGet(String xNetworkID, String xNetworkSecret, String playerId) {
            onlineTimeGetCalls++;
            lastOnlineTimePlayerId = playerId;

            ModelsPlayerOnlineTimeResponse response = new ModelsPlayerOnlineTimeResponse();
            response.setPlayerId(playerId);
            response.setOnlineTimeSeconds(onlineTimeSeconds);
            return response;
        }

        @Override
        public ModelsPlayerResponse v0PlayersPatch(
                String xNetworkID,
                String xNetworkSecret,
                String playerId,
                ModelsPatchPlayerRequest modelsPatchPlayerRequest
        ) {
            patchCalls++;
            lastPatchPlayerId = playerId;
            lastPatchRequest = modelsPatchPlayerRequest;
            onlineTimeSeconds = modelsPatchPlayerRequest.getOnlineTimeSeconds();
            return playerResponse(onlineTimeSeconds);
        }
    }
}
