package app.simplecloud.api.internal.networkstate;

import build.buf.gen.simplecloud.controller.v2.StateBlueprint;
import build.buf.gen.simplecloud.controller.v2.StatePersistentServer;
import build.buf.gen.simplecloud.controller.v2.StateServer;
import build.buf.gen.simplecloud.controller.v2.StateServerGroup;
import build.buf.gen.simplecloud.controller.v2.StateServerMetrics;
import com.google.protobuf.ByteString;

import java.util.Map;

/**
 * A view's copy of its network: the items as they were in the database at {@code revision} of
 * {@code epoch}, and the metrics of the last pulse or snapshot. Each map is keyed by the item's 16-byte id
 * and cannot be changed.
 *
 * @param metrics per server id; a server without an entry shows zeros, as HTTP does
 */
public record NetworkCopy(
        long epoch,
        long revision,
        Map<ByteString, StateServer> servers,
        Map<ByteString, StateServerGroup> serverGroups,
        Map<ByteString, StatePersistentServer> persistentServers,
        Map<ByteString, StateBlueprint> blueprints,
        Map<ByteString, StateServerMetrics> metrics
) {

    public NetworkCopy {
        servers = Map.copyOf(servers);
        serverGroups = Map.copyOf(serverGroups);
        persistentServers = Map.copyOf(persistentServers);
        blueprints = Map.copyOf(blueprints);
        metrics = Map.copyOf(metrics);
    }
}
