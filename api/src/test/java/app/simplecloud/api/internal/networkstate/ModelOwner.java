package app.simplecloud.api.internal.networkstate;

import build.buf.gen.simplecloud.controller.v2.StateBatch;
import build.buf.gen.simplecloud.controller.v2.StateBlueprint;
import build.buf.gen.simplecloud.controller.v2.StatePersistentServer;
import build.buf.gen.simplecloud.controller.v2.StatePulse;
import build.buf.gen.simplecloud.controller.v2.StateServer;
import build.buf.gen.simplecloud.controller.v2.StateServerGroup;
import build.buf.gen.simplecloud.controller.v2.StateServerMetrics;
import com.google.protobuf.ByteString;

import java.util.HashSet;
import java.util.NavigableMap;
import java.util.Random;
import java.util.Set;

/**
 * The simulator's relay in one controller while it owns the network (design 4.2 and 4.3). Between rounds it
 * remembers only what its last successful round read, so a new owner starts from the stored number.
 */
final class ModelOwner {

    private static final long PULSE_NEEDS_ROUND_WITHIN_MILLIS = 3_000;

    private Long lastRoundAt;
    private long roundEpoch;
    private long roundRevision;

    /**
     * Runs one relay round on one consistent read of the database, and returns the batch to publish, or null
     * when nothing is new. The caller stores the batch's revision as the last relayed number afterwards.
     */
    StateBatch runRound(ModelDatabase.State read, long now) {
        long lastRelayed = read.lastRelayed();
        long head = read.counter();
        lastRoundAt = now;
        roundEpoch = read.epoch();
        roundRevision = Math.max(lastRelayed, head);
        if (head <= lastRelayed) {
            return null;
        }
        NavigableMap<Long, Set<ModelDatabase.EntityKey>> unrelayed =
                read.changeLog().subMap(lastRelayed, false, head, true);
        boolean entriesMissing = unrelayed.size() < head - lastRelayed;
        if (entriesMissing) {
            return batchThatMakesViewsBehindReload(read.epoch(), head);
        }
        return batchOfChanges(read, lastRelayed, unrelayed);
    }

    /**
     * Returns the pulse, or null when the last successful round is 3 s old or older. Its revision is the
     * last relayed number as of that round, in that round's epoch.
     */
    StatePulse pulse(ModelDatabase.State read, long now, Random random) {
        boolean keepingUp = lastRoundAt != null && now - lastRoundAt < PULSE_NEEDS_ROUND_WITHIN_MILLIS;
        if (!keepingUp) {
            return null;
        }
        StatePulse.Builder pulse = StatePulse.newBuilder()
                .setEpoch(roundEpoch)
                .setRevision(roundRevision)
                .setViewsMayAnswer(true);
        for (StateServer server : read.items().servers().values()) {
            StateServerMetrics metrics = StateServerMetrics.newBuilder()
                    .setServerId(server.getId())
                    .setPlayerCount(random.nextInt(50))
                    .build();
            pulse.addMetrics(metrics);
        }
        return pulse.build();
    }

    private static StateBatch batchThatMakesViewsBehindReload(long epoch, long head) {
        return StateBatch.newBuilder()
                .setEpoch(epoch)
                .setBaseRevision(head)
                .setRevision(head)
                .build();
    }

    private static StateBatch batchOfChanges(ModelDatabase.State read, long lastRelayed,
                                             NavigableMap<Long, Set<ModelDatabase.EntityKey>> unrelayed) {
        Set<ModelDatabase.EntityKey> changed = new HashSet<>();
        for (Set<ModelDatabase.EntityKey> entities : unrelayed.values()) {
            changed.addAll(entities);
        }
        StateBatch.Builder batch = StateBatch.newBuilder()
                .setEpoch(read.epoch())
                .setBaseRevision(lastRelayed)
                .setRevision(read.counter());
        for (ModelDatabase.EntityKey entity : changed) {
            addRowOrDeletedId(batch, read.items(), entity);
        }
        return batch.build();
    }

    private static void addRowOrDeletedId(StateBatch.Builder batch, ModelDatabase.Items items,
                                          ModelDatabase.EntityKey entity) {
        ByteString id = entity.id();
        switch (entity.kind()) {
            case SERVER -> {
                StateServer row = items.servers().get(id);
                if (row != null) {
                    batch.addServers(row);
                } else {
                    batch.addDeletedServerIds(id);
                }
            }
            case SERVER_GROUP -> {
                StateServerGroup row = items.serverGroups().get(id);
                if (row != null) {
                    batch.addServerGroups(row);
                } else {
                    batch.addDeletedServerGroupIds(id);
                }
            }
            case PERSISTENT_SERVER -> {
                StatePersistentServer row = items.persistentServers().get(id);
                if (row != null) {
                    batch.addPersistentServers(row);
                } else {
                    batch.addDeletedPersistentServerIds(id);
                }
            }
            case BLUEPRINT -> {
                StateBlueprint row = items.blueprints().get(id);
                if (row != null) {
                    batch.addBlueprints(row);
                } else {
                    batch.addDeletedBlueprintIds(id);
                }
            }
        }
    }
}
