package app.simplecloud.api.internal.networkstate.read;

import app.simplecloud.api.CloudApiException;
import app.simplecloud.api.group.Group;
import app.simplecloud.api.group.GroupQuery;
import app.simplecloud.api.internal.group.ServerGroupSource;
import app.simplecloud.api.internal.networkstate.NetworkCopy;
import app.simplecloud.api.internal.persistentserver.PersistentServerSource;
import app.simplecloud.api.internal.server.ServerSource;
import app.simplecloud.api.persistentserver.PersistentServer;
import app.simplecloud.api.persistentserver.PersistentServerQuery;
import app.simplecloud.api.server.Server;
import app.simplecloud.api.server.ServerQuery;
import build.buf.gen.simplecloud.controller.v2.StatePersistentServer;
import build.buf.gen.simplecloud.controller.v2.StateServer;
import build.buf.gen.simplecloud.controller.v2.StateServerGroup;
import build.buf.gen.simplecloud.controller.v2.StateServerMetrics;
import com.google.protobuf.ByteString;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReadableCopyTest {

    private static final String NETWORK_ID = "0d000000-0000-4000-8000-000000000001";
    private static final String SERVER_ID = "0d000000-0000-4000-8000-000000000201";
    private static final ByteString SERVER_ID_BYTES = ByteString.fromHex("0d000000000040008000000000000201");
    private static final ByteString GROUP_ID_BYTES = ByteString.fromHex("0d000000000040008000000000000301");
    private static final ByteString PERSISTENT_SERVER_ID_BYTES = ByteString.fromHex("0d000000000040008000000000000401");

    private static final StateServerGroup GROUP = StateServerGroup.newBuilder()
            .setId(GROUP_ID_BYTES)
            .setInfoJson(json("{\"id\":\"0d000000-0000-4000-8000-000000000301\",\"name\":\"lobby\","
                    + "\"type\":\"SERVER\",\"source\":{\"type\":\"image\",\"image\":\"lobby:latest\"}}"))
            .setSummaryJson(json("{\"server_group_id\":\"0d000000-0000-4000-8000-000000000301\",\"name\":\"lobby\","
                    + "\"type\":\"SERVER\",\"created_at\":\"2026-11-17T09:00:00Z\"}"))
            .build();
    private static final StatePersistentServer PERSISTENT_SERVER = StatePersistentServer.newBuilder()
            .setId(PERSISTENT_SERVER_ID_BYTES)
            .setInfoJson(json("{\"id\":\"0d000000-0000-4000-8000-000000000401\",\"name\":\"survival\"}"))
            .setSummaryJson(json("{\"persistent_server_id\":\"0d000000-0000-4000-8000-000000000401\","
                    + "\"name\":\"survival\",\"created_at\":\"2026-11-17T09:00:00Z\"}"))
            .build();
    private static final StateServer SERVER = StateServer.newBuilder()
            .setId(SERVER_ID_BYTES)
            .setServerGroupId(GROUP_ID_BYTES)
            .setNumericalId(1)
            .setState("AVAILABLE")
            .setPropertiesJson(json("{}"))
            .build();
    private static final NetworkCopy NETWORK_COPY = new NetworkCopy(1, 1, Map.of(SERVER_ID_BYTES, SERVER),
            Map.of(GROUP_ID_BYTES, GROUP), Map.of(PERSISTENT_SERVER_ID_BYTES, PERSISTENT_SERVER), Map.of(), Map.of());
    private static final ReadableCopy COPY = new ReadableCopy(NETWORK_COPY, NETWORK_ID);
    private static final ServerSource SERVERS = COPY.servers();
    private static final ServerGroupSource SERVER_GROUPS = COPY.serverGroups();
    private static final PersistentServerSource PERSISTENT_SERVERS = COPY.persistentServers();

    @Test
    void anUnknownServerSortFieldIsAnInvalidRequest() {
        ServerQuery unknownField = ServerQuery.create().sortBy("name");

        CloudApiException thrown = assertThrows(CloudApiException.class, () -> SERVERS.servers(unknownField));

        assertEquals(CloudApiException.Reason.INVALID_REQUEST, thrown.getReason());
    }

    @Test
    void anUnknownServerSortOrderIsAnInvalidRequest() {
        ServerQuery unknownOrder = ServerQuery.create().sortBy("created_at").sortOrder("up");

        CloudApiException thrown = assertThrows(CloudApiException.class, () -> SERVERS.servers(unknownOrder));

        assertEquals(CloudApiException.Reason.INVALID_REQUEST, thrown.getReason());
    }

    @Test
    void aGroupLimitBelowOneIsAnInvalidRequest() {
        GroupQuery zeroLimit = GroupQuery.create().limit(0);

        CloudApiException thrown = assertThrows(CloudApiException.class, () -> SERVER_GROUPS.serverGroups(zeroLimit));

        assertEquals(CloudApiException.Reason.INVALID_REQUEST, thrown.getReason());
    }

    @Test
    void aPersistentServerLimitBelowOneIsAnInvalidRequest() {
        PersistentServerQuery negativeLimit = PersistentServerQuery.create().limit(-1);

        CloudApiException thrown = assertThrows(CloudApiException.class,
                () -> PERSISTENT_SERVERS.persistentServers(negativeLimit));

        assertEquals(CloudApiException.Reason.INVALID_REQUEST, thrown.getReason());
    }

    @Test
    void anIdThatIsNoUuidMatchesNothing() {
        ServerQuery byGroupName = ServerQuery.create().filterByServerGroupId("lobby");
        ServerQuery byServerhostName = ServerQuery.create().filterByServerhostId("h1");

        assertNull(SERVERS.serverWithId("server-1").join());
        assertNull(SERVER_GROUPS.serverGroupWithId("lobby").join());
        assertNull(PERSISTENT_SERVERS.persistentServerWithId("survival").join());
        assertEquals(List.of(), SERVERS.servers(byGroupName).join());
        assertEquals(List.of(), SERVERS.servers(byServerhostName).join());
    }

    @Test
    void aMetricThatIsNotAFiniteNumberIsNull() {
        StateServerMetrics notANumber = StateServerMetrics.newBuilder()
                .setServerId(SERVER_ID_BYTES)
                .setCpuUsage(Double.NaN)
                .setMemoryUsage(0.5)
                .build();
        ReadableCopy copy = new ReadableCopy(new NetworkCopy(1, 1, Map.of(SERVER_ID_BYTES, SERVER),
                Map.of(GROUP_ID_BYTES, GROUP), Map.of(), Map.of(), Map.of(SERVER_ID_BYTES, notANumber)), NETWORK_ID);

        ServerSource servers = copy.servers();
        Server server = servers.serverWithId(SERVER_ID).join();

        assertNull(server.getCpuUsage());
        assertEquals(0.5, server.getMemoryUsage());
    }

    @Test
    void aServerWhoseGroupTheCopyLacksFailsTheBuildOnceForThatCopy() {
        ReadableCopy copy = new ReadableCopy(new NetworkCopy(1, 1, Map.of(SERVER_ID_BYTES, SERVER), Map.of(),
                Map.of(), Map.of(), Map.of()), NETWORK_ID);

        IllegalStateException first = assertThrows(IllegalStateException.class, copy::servers);
        IllegalStateException second = assertThrows(IllegalStateException.class, copy::servers);

        assertSame(first, second);
    }

    @Test
    void everyReadOfOneCopyGetsTheSameObjects() {
        Server serverOfList = SERVERS.servers(null).join().getFirst();
        Group groupOfList = SERVER_GROUPS.serverGroups(null).join().getFirst();
        PersistentServer persistentServerOfList = PERSISTENT_SERVERS.persistentServers(null).join().getFirst();
        Server serverById = SERVERS.serverWithId(SERVER_ID).join();
        Group groupByName = SERVER_GROUPS.serverGroupWithName("lobby").join();
        PersistentServer persistentServerByName = PERSISTENT_SERVERS.persistentServerWithName("survival").join();

        assertSame(serverOfList, serverById);
        assertSame(groupOfList, groupByName);
        assertSame(persistentServerOfList, persistentServerByName);
        assertSame(SERVERS, COPY.servers());
    }

    private static ByteString json(String text) {
        return ByteString.copyFromUtf8(text);
    }
}
