package app.simplecloud.api.internal.integration.player;

import build.buf.gen.simplecloud.player.v2.ConnectPlayerRequest;
import build.buf.gen.simplecloud.player.v2.ConnectPlayerToGroupRequest;
import build.buf.gen.simplecloud.player.v2.ServerSelectionMode;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectTargetTest {

    private static final String SERVER_ID = "11111111-2222-3333-4444-555555555555";

    private final RegisteredServers<String> registeredServers = new FakeRegisteredServers(
            Map.of(
                    "lobby-2", "lobby-2-server",
                    "lobby", "lobby-server",
                    SERVER_ID, "server-named-like-the-id"
            ),
            Map.of(SERVER_ID, "lobby-1-server")
    );

    @Test
    void resolve_byName_findsServerByName() {
        ConnectTarget target = ConnectTarget.from(requestByName("lobby-2"));

        assertEquals(Optional.of("lobby-2-server"), target.resolve(registeredServers));
    }

    @Test
    void resolve_byUnknownName_returnsEmpty() {
        ConnectTarget target = ConnectTarget.from(requestByName("lobby-3"));

        assertTrue(target.resolve(registeredServers).isEmpty());
    }

    @Test
    void resolve_byNameHoldingAnId_findsServerByName() {
        ConnectTarget target = ConnectTarget.from(requestByName(SERVER_ID));

        assertEquals(Optional.of("server-named-like-the-id"), target.resolve(registeredServers));
    }

    @Test
    void resolve_byId_findsServerById() {
        ConnectTarget target = ConnectTarget.from(requestById(SERVER_ID));

        assertEquals(Optional.of("lobby-1-server"), target.resolve(registeredServers));
    }

    @Test
    void resolve_byUnknownId_returnsEmpty() {
        ConnectTarget target = ConnectTarget.from(requestById("99999999-2222-3333-4444-555555555555"));

        assertTrue(target.resolve(registeredServers).isEmpty());
    }

    @Test
    void resolve_byIdAndName_prefersId() {
        ConnectPlayerRequest request = ConnectPlayerRequest.newBuilder()
                .setServerId(SERVER_ID)
                .setServerName("lobby-2")
                .build();
        ConnectTarget target = ConnectTarget.from(request);

        assertEquals(Optional.of("lobby-1-server"), target.resolve(registeredServers));
    }

    @Test
    void resolve_byUnknownIdAndName_doesNotFallBackToName() {
        ConnectPlayerRequest request = ConnectPlayerRequest.newBuilder()
                .setServerId("99999999-2222-3333-4444-555555555555")
                .setServerName("lobby-2")
                .build();
        ConnectTarget target = ConnectTarget.from(request);

        assertTrue(target.resolve(registeredServers).isEmpty());
    }

    @Test
    void resolve_groupRequestDecodedAsConnectRequest_staysOnNameBranch() throws Exception {
        // Today's controller sends ConnectPlayerToGroupRequest on the connect subject for group connects.
        // Its selection_mode is field 3 like server_id, but a varint, so it must not become an ID connect.
        ConnectPlayerToGroupRequest groupRequest = ConnectPlayerToGroupRequest.newBuilder()
                .setGroupName("lobby")
                .setSelectionMode(ServerSelectionMode.SERVER_SELECTION_MODE_MIN_PLAYERS)
                .build();
        ConnectPlayerRequest decoded = ConnectPlayerRequest.parseFrom(groupRequest.toByteArray());
        ConnectTarget target = ConnectTarget.from(decoded);

        assertEquals("", decoded.getServerId());
        assertEquals(Optional.of("lobby-server"), target.resolve(registeredServers));
    }

    private static ConnectPlayerRequest requestByName(String serverName) {
        return ConnectPlayerRequest.newBuilder().setServerName(serverName).build();
    }

    private static ConnectPlayerRequest requestById(String serverId) {
        return ConnectPlayerRequest.newBuilder().setServerId(serverId).build();
    }
}
