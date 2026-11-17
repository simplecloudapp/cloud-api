package app.simplecloud.api.internal.networkstate.read;

import app.simplecloud.api.internal.networkstate.NetworkCopy;
import build.buf.gen.simplecloud.controller.v2.StateServer;
import build.buf.gen.simplecloud.controller.v2.StateServerGroup;
import build.buf.gen.simplecloud.controller.v2.StateServerMetrics;
import com.google.protobuf.ByteString;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CopyServerListTest {

    private static final ByteString NETWORK_ID = ByteString.fromHex("0d000000000040008000000000000001");
    private static final ByteString GROUP_ID = ByteString.fromHex("0d000000000040008000000000000301");
    private static final ByteString SERVER_ID = ByteString.fromHex("0d000000000040008000000000000201");

    private static final StateServerGroup GROUP = StateServerGroup.newBuilder()
            .setId(GROUP_ID)
            .setInfoJson(ByteString.copyFromUtf8(
                    "{\"id\":\"0d000000-0000-4000-8000-000000000301\",\"name\":\"lobby\",\"type\":\"SERVER\","
                            + "\"source\":{\"type\":\"image\",\"image\":\"lobby:latest\"}}"))
            .build();
    private static final StateServer SERVER = StateServer.newBuilder()
            .setId(SERVER_ID)
            .setServerGroupId(GROUP_ID)
            .setNumericalId(1)
            .setState("AVAILABLE")
            .setPropertiesJson(ByteString.copyFromUtf8("{}"))
            .build();

    @Test
    void aMetricTheControllerCannotEncodeLeavesTheAnswerToTheController() {
        StateServerMetrics notANumber = StateServerMetrics.newBuilder()
                .setServerId(SERVER_ID)
                .setCpuUsage(Double.NaN)
                .build();
        NetworkCopy copy = new NetworkCopy(1, 1, Map.of(SERVER_ID, SERVER), Map.of(GROUP_ID, GROUP), Map.of(),
                Map.of(), Map.of(SERVER_ID, notANumber));

        assertTrue(CopyServerList.answer(copy, NETWORK_ID, ListParameters.servers()).isEmpty());
    }

    @Test
    void aServerWhoseGroupTheCopyLacksIsABugInTheView() {
        NetworkCopy copy = new NetworkCopy(1, 1, Map.of(SERVER_ID, SERVER), Map.of(), Map.of(), Map.of(), Map.of());

        assertThrows(IllegalStateException.class,
                () -> CopyServerList.answer(copy, NETWORK_ID, ListParameters.servers()));
    }
}
