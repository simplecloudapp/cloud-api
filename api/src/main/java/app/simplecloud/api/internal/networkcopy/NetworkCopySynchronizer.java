package app.simplecloud.api.internal.networkcopy;

import app.simplecloud.api.internal.web.Identifiers;
import build.buf.gen.simplecloud.controller.v2.NetworkState;
import build.buf.gen.simplecloud.controller.v2.StateBatch;
import build.buf.gen.simplecloud.controller.v2.StatePulse;
import com.google.protobuf.InvalidProtocolBufferException;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.Message;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.LongSupplier;
import java.util.logging.Logger;

/**
 * Keeps the network copy in step with the controller: loads it over HTTP, applies the change messages from NATS,
 * and hands out the current contents to reads.
 */
public final class NetworkCopySynchronizer {
    private static final Logger LOGGER = Logger.getLogger(NetworkCopySynchronizer.class.getName());
    private static final Duration PULSE_SILENCE_LIMIT = Duration.ofSeconds(15);
    private static final Duration OWN_WRITE_WAIT_LIMIT = Duration.ofSeconds(3);
    private static final Duration OWN_WRITE_POLL_INTERVAL = Duration.ofMillis(20);
    private static final Duration LOAD_TIME_LIMIT = Duration.ofSeconds(10);
    private static final Duration FRESH_LOAD_INTERVAL = Duration.ofMinutes(30);

    private final OkHttpClient httpClient;
    private final Request snapshotRequest;
    private final String networkId;
    private final LongSupplier nanoClock;
    private @Nullable Connection natsConnection;
    private @Nullable Dispatcher natsDispatcher;

    // Changed only inside handlePulse and handleBatch.
    private @Nullable NetworkCopy networkCopy;
    private boolean loadRefused;
    private long lastLoadNanos;

    // Read by any thread.
    private volatile @Nullable NetworkContents latestContents;
    private volatile long lastPulseNanos;

    /**
     * A synchronizer that is not started yet.
     */
    public NetworkCopySynchronizer(OkHttpClient httpClient, String controllerUrl, String networkId, String networkSecret) {
        this(httpClient, controllerUrl, networkId, networkSecret, System::nanoTime);
    }

    NetworkCopySynchronizer(OkHttpClient httpClient, String controllerUrl, String networkId, String networkSecret, LongSupplier nanoClock) {
        this.httpClient = httpClient.newBuilder().callTimeout(LOAD_TIME_LIMIT).build();
        this.snapshotRequest = new Request.Builder()
                .url(controllerUrl + "/v0/network-state")
                .header("X-Network-ID", networkId)
                .header("X-Network-Credential", networkSecret)
                .header("Accept", "application/x-protobuf")
                .build();
        // Lower case, as the controller writes it in its subjects and lists.
        this.networkId = networkId.toLowerCase(Locale.ROOT);
        this.nanoClock = nanoClock;
    }

    /**
     * A synchronizer that never has contents, for API objects built without a shared runtime.
     */
    public static NetworkCopySynchronizer createNeverStarted() {
        return new NetworkCopySynchronizer(new OkHttpClient(), "http://localhost", "", "");
    }

    /**
     * Subscribes to the network's batch and pulse subjects; does nothing if the network id is no UUID.
     */
    public void start(Connection natsConnection) {
        if (!Identifiers.isUuid(networkId)) {
            // The controller publishes under the network's UUID. An instance set up with another id gets no messages.
            return;
        }
        String subjectRoot = networkId + ".state.v1.";
        // One dispatcher for both subjects, so batches and pulses arrive in the order the controller sent them.
        natsDispatcher = natsConnection.createDispatcher();
        natsDispatcher.subscribe(subjectRoot + "batch", this::handleBatchMessage);
        natsDispatcher.subscribe(subjectRoot + "pulse", this::handlePulseMessage);
        this.natsConnection = natsConnection;
    }

    /**
     * Ends the subscriptions; does nothing on a synchronizer that was never started.
     */
    public void close() {
        if (natsDispatcher == null) {
            return;
        }
        try {
            natsConnection.closeDispatcher(natsDispatcher);
        } catch (IllegalStateException connectionAlreadyClosed) {
            // A closed connection has no subscriptions left, so there is nothing to end.
        }
    }

    /**
     * The current contents, or null while reads must go to the controller.
     */
    public @Nullable NetworkContents currentContents() {
        // The time first: a pulse writes its contents before its time, so a fresh time means contents at least as new.
        long pulseNanos = lastPulseNanos;
        NetworkContents contents = latestContents;
        if (contents == null) {
            return null;
        }
        long nanosSincePulse = nanoClock.getAsLong() - pulseNanos;
        if (nanosSincePulse > PULSE_SILENCE_LIMIT.toNanos()) {
            return null;
        }
        return contents;
    }

    /**
     * Returns when the copy holds the write these response headers belong to, or after 3 seconds.
     * Returns at once when the controller sent no revision headers, or while reads go to the controller.
     */
    public void waitForOwnWrite(Map<String, List<String>> responseHeaders) {
        Long epoch = parseHeaderNumber(responseHeaders, "x-sc-state-epoch");
        Long revision = parseHeaderNumber(responseHeaders, "x-sc-state-revision");
        if (epoch == null || revision == null) {
            return;
        }
        // The real time, not the synchronizer's clock: the loop sleeps in real time.
        long waitStartNanos = System.nanoTime();
        while (usableCopyLacksWrite(epoch, revision)) {
            long waitedNanos = System.nanoTime() - waitStartNanos;
            boolean waitedLongEnough = waitedNanos >= OWN_WRITE_WAIT_LIMIT.toNanos();
            if (waitedLongEnough) {
                return;
            }
            boolean interrupted = sleepAndCheckInterrupted(OWN_WRITE_POLL_INTERVAL);
            if (interrupted) {
                return;
            }
        }
    }

    private void handlePulseMessage(Message message) {
        StatePulse pulse;
        try {
            pulse = StatePulse.parseFrom(message.getData());
        } catch (InvalidProtocolBufferException e) {
            LOGGER.warning("Ignored a network state pulse that could not be read: " + e);
            return;
        }
        handlePulse(pulse);
    }

    private void handleBatchMessage(Message message) {
        StateBatch batch;
        try {
            batch = StateBatch.parseFrom(message.getData());
        } catch (InvalidProtocolBufferException e) {
            LOGGER.warning("Ignored a network state batch that could not be read: " + e);
            return;
        }
        handleBatch(batch);
    }

    // Synchronized with handleBatch: while the NATS connection is replaced, two NATS threads can deliver for a moment.
    synchronized void handlePulse(StatePulse pulse) {
        if (loadRefused) {
            return;
        }
        if (!pulse.getViewsMayAnswer()) {
            forgetCopy("the controller turned copy answers off");
            return;
        }
        try {
            if (mustLoad(pulse)) {
                networkCopy = loadNetworkCopy();
                lastLoadNanos = nanoClock.getAsLong();
            } else {
                networkCopy.setMetrics(pulse.getMetricsList());
            }
            latestContents = networkCopy.buildContents();
            lastPulseNanos = nanoClock.getAsLong();
        } catch (SnapshotRefusedException e) {
            // The controller gives the full state only to the network password. A JWT never gets it.
            loadRefused = true;
            networkCopy = null;
            latestContents = null;
            LOGGER.warning("Network copy is off for this connection: the controller refuses the network state to this credential");
        } catch (IOException | RuntimeException e) {
            forgetCopy("the network state could not be loaded or read: " + e);
        }
    }

    synchronized void handleBatch(StateBatch batch) {
        if (networkCopy == null) {
            return;
        }
        boolean otherEpoch = batch.getEpoch() != networkCopy.epoch();
        int baseComparedToCopy = Long.compareUnsigned(batch.getBaseRevision(), networkCopy.revision());
        boolean startsAfterCopy = baseComparedToCopy > 0;
        if (otherEpoch || startsAfterCopy) {
            forgetCopy("a change message did not fit the copy");
            return;
        }
        int revisionComparedToCopy = Long.compareUnsigned(batch.getRevision(), networkCopy.revision());
        boolean alreadyApplied = revisionComparedToCopy <= 0;
        if (alreadyApplied) {
            return;
        }
        try {
            networkCopy.applyBatch(batch);
            latestContents = networkCopy.buildContents();
        } catch (RuntimeException e) {
            forgetCopy("a change message could not be read: " + e);
        }
    }

    private boolean mustLoad(StatePulse pulse) {
        if (networkCopy == null) {
            return true;
        }
        boolean otherEpoch = networkCopy.epoch() != pulse.getEpoch();
        if (otherEpoch) {
            return true;
        }
        int pulseComparedToCopy = Long.compareUnsigned(pulse.getRevision(), networkCopy.revision());
        boolean pulseIsAheadOfCopy = pulseComparedToCopy > 0;
        if (pulseIsAheadOfCopy) {
            return true;
        }
        // A fresh load limits how long a change that never reached the copy can stay wrong.
        long nanosSinceLoad = nanoClock.getAsLong() - lastLoadNanos;
        boolean lastLoadIsOld = nanosSinceLoad >= FRESH_LOAD_INTERVAL.toNanos();
        return lastLoadIsOld;
    }

    private NetworkCopy loadNetworkCopy() throws IOException {
        try (Response response = httpClient.newCall(snapshotRequest).execute()) {
            if (response.code() == HttpURLConnection.HTTP_FORBIDDEN) {
                throw new SnapshotRefusedException();
            }
            if (!response.isSuccessful()) {
                throw new IOException("GET /v0/network-state answered " + response.code());
            }
            NetworkState snapshot = NetworkState.parseFrom(response.body().bytes());
            return NetworkCopy.createFromSnapshot(snapshot, networkId);
        }
    }

    private static final class SnapshotRefusedException extends IOException {
    }

    private void forgetCopy(String reason) {
        if (latestContents != null) {
            LOGGER.info("Network copy dropped, reads go to the controller: " + reason);
        }
        networkCopy = null;
        latestContents = null;
    }

    private boolean usableCopyLacksWrite(long epoch, long revision) {
        NetworkContents contents = currentContents();
        if (contents == null) {
            // Reads go to the controller then, so there is nothing to wait for.
            return false;
        }
        int copyComparedToWrite = Long.compareUnsigned(contents.revision(), revision);
        return contents.epoch() != epoch || copyComparedToWrite < 0;
    }

    // A header that is missing or not a number counts as no header: the write itself already succeeded.
    private static @Nullable Long parseHeaderNumber(Map<String, List<String>> headers, String name) {
        List<String> values = headers.get(name);
        if (values == null || values.isEmpty()) {
            return null;
        }
        try {
            return Long.parseUnsignedLong(values.getFirst());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // The interrupt stays set for the caller.
    private static boolean sleepAndCheckInterrupted(Duration duration) {
        try {
            Thread.sleep(duration);
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return true;
        }
    }
}
