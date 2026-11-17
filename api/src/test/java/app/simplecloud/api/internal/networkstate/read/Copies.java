package app.simplecloud.api.internal.networkstate.read;

import app.simplecloud.api.internal.networkstate.NetworkCopy;
import build.buf.gen.simplecloud.controller.v2.NetworkState;
import build.buf.gen.simplecloud.controller.v2.StateBlueprint;
import build.buf.gen.simplecloud.controller.v2.StatePersistentServer;
import build.buf.gen.simplecloud.controller.v2.StateServer;
import build.buf.gen.simplecloud.controller.v2.StateServerGroup;
import build.buf.gen.simplecloud.controller.v2.StateServerMetrics;
import com.google.protobuf.ByteString;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Copies as a view holds them after taking a snapshot.
 */
final class Copies {

    private Copies() {
    }

    static NetworkCopy of(NetworkState snapshot) {
        return new NetworkCopy(
                snapshot.getEpoch(),
                snapshot.getRevision(),
                byId(snapshot.getServersList(), StateServer::getId),
                byId(snapshot.getServerGroupsList(), StateServerGroup::getId),
                byId(snapshot.getPersistentServersList(), StatePersistentServer::getId),
                byId(snapshot.getBlueprintsList(), StateBlueprint::getId),
                byId(snapshot.getMetricsList(), StateServerMetrics::getServerId));
    }

    private static <T> Map<ByteString, T> byId(List<T> items, Function<T, ByteString> idOf) {
        Map<ByteString, T> byId = new HashMap<>();
        for (T item : items) {
            byId.put(idOf.apply(item), item);
        }
        return byId;
    }
}
