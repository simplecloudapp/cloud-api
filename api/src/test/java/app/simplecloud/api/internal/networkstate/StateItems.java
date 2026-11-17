package app.simplecloud.api.internal.networkstate;

import build.buf.gen.simplecloud.controller.v2.NetworkState;
import build.buf.gen.simplecloud.controller.v2.StateBatch;
import build.buf.gen.simplecloud.controller.v2.StateBlueprint;
import build.buf.gen.simplecloud.controller.v2.StatePersistentServer;
import build.buf.gen.simplecloud.controller.v2.StatePulse;
import build.buf.gen.simplecloud.controller.v2.StateServer;
import build.buf.gen.simplecloud.controller.v2.StateServerGroup;
import build.buf.gen.simplecloud.controller.v2.StateServerMetrics;
import com.google.protobuf.ByteString;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Small items and messages for the example checks. Ids are numbers, so a check reads as "server 1 in
 * group 10".
 */
final class StateItems {

    static final long EPOCH = 7;

    private StateItems() {
    }

    static ByteString id(int number) {
        ByteBuffer bytes = ByteBuffer.allocate(16);
        bytes.putLong(0x5c5c5c5c5c5c5c5cL);
        bytes.putLong(number);
        return ByteString.copyFrom(bytes.array());
    }

    static ItemId serverItem(int number) {
        return new ItemId(ItemKind.SERVER, id(number));
    }

    static StateServer groupServer(int number, int groupNumber, String state) {
        return StateServer.newBuilder()
                .setId(id(number))
                .setServerGroupId(id(groupNumber))
                .setNumericalId(number)
                .setState(state)
                .setPropertiesJson(json("{}"))
                .build();
    }

    static StateServer persistentServerInstance(int number, int persistentServerNumber) {
        return StateServer.newBuilder()
                .setId(id(number))
                .setPersistentServerId(id(persistentServerNumber))
                .setNumericalId(number)
                .setState("AVAILABLE")
                .setPropertiesJson(json("{}"))
                .build();
    }

    static StateServerGroup group(int number) {
        return StateServerGroup.newBuilder()
                .setId(id(number))
                .setInfoJson(json("{\"name\":\"group-" + number + "\"}"))
                .setSummaryJson(json("{\"name\":\"group-" + number + "\"}"))
                .build();
    }

    static StateServerGroup groupOfBlueprint(int number, int blueprintNumber) {
        return group(number).toBuilder().setBlueprintId(id(blueprintNumber)).build();
    }

    static StatePersistentServer persistentServer(int number) {
        return StatePersistentServer.newBuilder()
                .setId(id(number))
                .setInfoJson(json("{\"name\":\"persistent-" + number + "\"}"))
                .setSummaryJson(json("{\"name\":\"persistent-" + number + "\"}"))
                .build();
    }

    static StateBlueprint blueprint(int number) {
        return StateBlueprint.newBuilder()
                .setId(id(number))
                .setInfoJson(json("{\"name\":\"blueprint-" + number + "\"}"))
                .build();
    }

    static StateServerMetrics metrics(int serverNumber, int playerCount) {
        return StateServerMetrics.newBuilder()
                .setServerId(id(serverNumber))
                .setPlayerCount(playerCount)
                .build();
    }

    /**
     * The network most checks start from: blueprint 30 with group 10 and its server 1, and persistent
     * server 20 with its server 2.
     */
    static NetworkState.Builder network(long epoch, long revision) {
        return NetworkState.newBuilder()
                .setEpoch(epoch)
                .setRevision(revision)
                .addBlueprints(blueprint(30))
                .addServerGroups(groupOfBlueprint(10, 30))
                .addPersistentServers(persistentServer(20))
                .addServers(groupServer(1, 10, "AVAILABLE"))
                .addServers(persistentServerInstance(2, 20))
                .addMetrics(metrics(1, 5));
    }

    static NetworkState network(long revision) {
        return network(EPOCH, revision).build();
    }

    static StateBatch.Builder batch(long epoch, long baseRevision, long revision) {
        return StateBatch.newBuilder()
                .setEpoch(epoch)
                .setBaseRevision(baseRevision)
                .setRevision(revision);
    }

    static StateBatch.Builder batch(long baseRevision, long revision) {
        return batch(EPOCH, baseRevision, revision);
    }

    static StateBatch serverStateBatch(long baseRevision, long revision, String state) {
        return batch(baseRevision, revision).addServers(groupServer(1, 10, state)).build();
    }

    static StatePulse pulse(long epoch, long revision) {
        return StatePulse.newBuilder()
                .setEpoch(epoch)
                .setRevision(revision)
                .setViewsMayAnswer(true)
                .build();
    }

    static StatePulse pulse(long revision) {
        return pulse(EPOCH, revision);
    }

    private static ByteString json(String text) {
        return ByteString.copyFrom(text, StandardCharsets.UTF_8);
    }
}
