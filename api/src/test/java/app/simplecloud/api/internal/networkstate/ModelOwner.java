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
 * The simulator's relay in one controller while it owns the network (design 4.2 and 4.3, with the
 * contract's decision on the pulse head). Between rounds it remembers what its last successful round read
 * and the last batch it published, so a new owner starts from the stored number.
 */
final class ModelOwner {

    /**
     * As often as the controller pulses.
     */
    static final long PULSE_INTERVAL_MILLIS = 5_000;
    private static final long PULSE_NEEDS_ROUND_WITHIN_MILLIS = 3_000;

    private Long lastRoundAt;
    private long roundEpoch;
    private long roundStoredNumber;
    private Long publishedEpoch;
    private long publishedRevision;

    /**
     * Runs one relay round on one consistent read of the database, and returns the batch to publish, or null
     * when nothing is new. The caller stores the batch's revision as the last relayed number afterwards.
     */
    StateBatch runRound(ModelDatabase.State read, long now) {
        long lastRelayed = read.lastRelayed();
        long head = read.counter();
        lastRoundAt = now;
        roundEpoch = read.epoch();
        roundStoredNumber = lastRelayed;
        if (head <= lastRelayed) {
            return null;
        }
        NavigableMap<Long, Set<ModelDatabase.EntityKey>> unrelayed =
                read.changeLog().subMap(lastRelayed, false, head, true);
        boolean entriesMissing = unrelayed.size() < head - lastRelayed;
        StateBatch batch = entriesMissing
                ? batchThatMakesViewsBehindReload(read.epoch(), head)
                : batchOfChanges(read, lastRelayed, unrelayed);
        publishedEpoch = batch.getEpoch();
        publishedRevision = batch.getRevision();
        return batch;
    }

    /**
     * Returns the pulse, or null when the last successful round is 3 s old or older. It states that round's
     * epoch, and the higher of the stored number that round read and this owner's last batch in that epoch.
     */
    StatePulse pulse(ModelDatabase.State read, long now, Random random) {
        boolean keepingUp = lastRoundAt != null && now - lastRoundAt < PULSE_NEEDS_ROUND_WITHIN_MILLIS;
        if (!keepingUp) {
            return null;
        }
        StatePulse.Builder pulse = StatePulse.newBuilder()
                .setEpoch(roundEpoch)
                .setRevision(pulseRevision())
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

    private long pulseRevision() {
        boolean publishedInRoundEpoch = publishedEpoch != null && publishedEpoch == roundEpoch;
        if (publishedInRoundEpoch) {
            return Math.max(roundStoredNumber, publishedRevision);
        }
        return roundStoredNumber;
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
        switch (entity.table()) {
            case SERVERS -> {
                StateServer row = items.servers().get(id);
                if (row != null) {
                    batch.addServers(row);
                } else {
                    batch.addDeletedServerIds(id);
                }
            }
            case SERVER_GROUPS -> {
                StateServerGroup row = items.serverGroups().get(id);
                if (row != null) {
                    batch.addServerGroups(row);
                } else {
                    batch.addDeletedServerGroupIds(id);
                }
            }
            case PERSISTENT_SERVERS -> {
                StatePersistentServer row = items.persistentServers().get(id);
                if (row != null) {
                    batch.addPersistentServers(row);
                } else {
                    batch.addDeletedPersistentServerIds(id);
                }
            }
            case BLUEPRINTS -> {
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
