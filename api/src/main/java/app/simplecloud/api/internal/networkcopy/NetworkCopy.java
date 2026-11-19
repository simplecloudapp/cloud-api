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
 * The plugin's live copy of its network: follows the controller and hands out the current contents.
 */
public final class NetworkCopy {
    private static final Logger LOGGER = Logger.getLogger(NetworkCopy.class.getName());
    private static final Duration PULSE_SILENCE_LIMIT = Duration.ofSeconds(15);
    private static final Duration OWN_WRITE_WAIT = Duration.ofSeconds(3);
    private static final Duration OWN_WRITE_POLL = Duration.ofMillis(20);
    private static final Duration LOAD_TIME_LIMIT = Duration.ofSeconds(10);
    private static final Duration FRESH_LOAD_INTERVAL = Duration.ofMinutes(30);

    private final OkHttpClient httpClient;
    private final Request networkStateRequest;
    private final String networkId;
    private final LongSupplier nanoTime;
    private @Nullable Connection natsConnection;
    private @Nullable Dispatcher dispatcher;

    // Changed only inside onPulse and onBatch.
    private @Nullable NetworkItems items;
    private boolean refused;
    private long lastLoadNanos;

    // Read by any thread.
    private volatile @Nullable NetworkContents contents;
    private volatile long lastPulseNanos;

    /**
     * A copy that is not started yet.
     */
    public NetworkCopy(OkHttpClient httpClient, String controllerUrl, String networkId, String networkSecret) {
        this(httpClient, controllerUrl, networkId, networkSecret, System::nanoTime);
    }

    NetworkCopy(OkHttpClient httpClient, String controllerUrl, String networkId, String networkSecret, LongSupplier nanoTime) {
        this.httpClient = httpClient.newBuilder().callTimeout(LOAD_TIME_LIMIT).build();
        this.networkStateRequest = new Request.Builder()
                .url(controllerUrl + "/v0/network-state")
                .header("X-Network-ID", networkId)
                .header("X-Network-Credential", networkSecret)
                .header("Accept", "application/x-protobuf")
                .build();
        this.networkId = networkId;
        this.nanoTime = nanoTime;
    }

    /**
     * A copy that never has contents, for API objects built without a shared runtime.
     */
    public static NetworkCopy absent() {
        // Never started, so it never loads.
        return new NetworkCopy(new OkHttpClient(), "http://localhost", "", "");
    }

    /**
     * Subscribes to the network's batch and pulse subjects; does nothing if the network id is no UUID.
     */
    public void start(Connection natsConnection) {
        if (!Identifiers.isUuid(networkId)) {
            // The controller publishes under the network's UUID. An instance set up with another id gets no messages.
            return;
        }
        String subjectRoot = networkId.toLowerCase(Locale.ROOT) + ".state.v1.";
        // One dispatcher for both subjects, so batches and pulses arrive in the order the controller sent them.
        dispatcher = natsConnection.createDispatcher();
        dispatcher.subscribe(subjectRoot + "batch", this::onBatchMessage);
        dispatcher.subscribe(subjectRoot + "pulse", this::onPulseMessage);
        this.natsConnection = natsConnection;
    }

    /**
     * Ends the subscriptions; does nothing on a copy that was never started.
     */
    public void close() {
        if (dispatcher == null) {
            return;
        }
        natsConnection.closeDispatcher(dispatcher);
    }

    /**
     * The current contents, or null while reads must go to the controller.
     */
    public @Nullable NetworkContents contents() {
        NetworkContents current = contents;
        if (current == null) {
            return null;
        }
        long nanosSincePulse = nanoTime.getAsLong() - lastPulseNanos;
        if (nanosSincePulse > PULSE_SILENCE_LIMIT.toNanos()) {
            return null;
        }
        return current;
    }

    /**
     * Returns when the copy holds the write these response headers belong to, or after 3 seconds.
     * Returns at once when the controller sent no revision headers, or while reads go to the controller.
     */
    public void waitForOwnWrite(Map<String, List<String>> responseHeaders) {
        Long epoch = headerNumber(responseHeaders, "x-sc-state-epoch");
        Long revision = headerNumber(responseHeaders, "x-sc-state-revision");
        if (epoch == null || revision == null) {
            return;
        }
        long giveUpAt = System.nanoTime() + OWN_WRITE_WAIT.toNanos();
        while (lacksWrite(epoch, revision) && System.nanoTime() < giveUpAt) {
            boolean interrupted = !sleep(OWN_WRITE_POLL);
            if (interrupted) {
                return;
            }
        }
    }

    // Synchronized with onBatch: while the NATS connection is replaced, two NATS threads can deliver for a moment.
    synchronized void onPulse(StatePulse pulse) {
        if (refused) {
            return;
        }
        if (!pulse.getViewsMayAnswer()) {
            forgetCopy("the controller turned copy answers off");
            return;
        }
        try {
            if (mustLoad(pulse)) {
                items = loadItems();
                lastLoadNanos = nanoTime.getAsLong();
            } else {
                items.setMetrics(pulse.getMetricsList());
            }
            NetworkContents newContents = items.toContents();
            // Before the contents, so a reader that sees these contents also sees the time of their pulse.
            lastPulseNanos = nanoTime.getAsLong();
            contents = newContents;
        } catch (IOException | RuntimeException e) {
            forgetCopy("the network state could not be loaded or read: " + e);
        }
    }

    synchronized void onBatch(StateBatch batch) {
        if (items == null) {
            return;
        }
        boolean otherEpoch = batch.getEpoch() != items.epoch();
        int baseComparedToCopy = Long.compareUnsigned(batch.getBaseRevision(), items.revision());
        boolean startsAfterCopy = baseComparedToCopy > 0;
        if (otherEpoch || startsAfterCopy) {
            forgetCopy("a change message did not fit the copy");
            return;
        }
        int revisionComparedToCopy = Long.compareUnsigned(batch.getRevision(), items.revision());
        boolean alreadyApplied = revisionComparedToCopy <= 0;
        if (alreadyApplied) {
            return;
        }
        try {
            items.apply(batch);
            contents = items.toContents();
        } catch (RuntimeException e) {
            forgetCopy("a change message could not be read: " + e);
        }
    }

    private void onPulseMessage(Message message) {
        StatePulse pulse;
        try {
            pulse = StatePulse.parseFrom(message.getData());
        } catch (InvalidProtocolBufferException e) {
            LOGGER.warning("Ignored a network state pulse that could not be read: " + e);
            return;
        }
        onPulse(pulse);
    }

    private void onBatchMessage(Message message) {
        StateBatch batch;
        try {
            batch = StateBatch.parseFrom(message.getData());
        } catch (InvalidProtocolBufferException e) {
            LOGGER.warning("Ignored a network state batch that could not be read: " + e);
            return;
        }
        onBatch(batch);
    }

    private boolean mustLoad(StatePulse pulse) {
        if (items == null) {
            return true;
        }
        boolean otherEpoch = items.epoch() != pulse.getEpoch();
        if (otherEpoch) {
            return true;
        }
        int pulseComparedToCopy = Long.compareUnsigned(pulse.getRevision(), items.revision());
        boolean pulseIsAheadOfCopy = pulseComparedToCopy > 0;
        if (pulseIsAheadOfCopy) {
            return true;
        }
        // A fresh load limits how long an item the relay missed can stay wrong.
        long nanosSinceLoad = nanoTime.getAsLong() - lastLoadNanos;
        boolean lastLoadIsOld = nanosSinceLoad >= FRESH_LOAD_INTERVAL.toNanos();
        return lastLoadIsOld;
    }

    private void forgetCopy(String reason) {
        if (contents != null) {
            LOGGER.info("Network copy dropped, reads go to the controller: " + reason);
        }
        items = null;
        contents = null;
    }

    private NetworkItems loadItems() throws IOException {
        try (Response response = httpClient.newCall(networkStateRequest).execute()) {
            if (response.code() == HttpURLConnection.HTTP_FORBIDDEN) {
                // The controller gives the full state only to the network password. A JWT never gets it.
                refused = true;
                throw new IOException("the controller refuses the network state to this credential");
            }
            if (!response.isSuccessful()) {
                throw new IOException("GET /v0/network-state answered " + response.code());
            }
            NetworkState snapshot = NetworkState.parseFrom(response.body().bytes());
            return NetworkItems.fromSnapshot(snapshot, networkId);
        }
    }

    private boolean lacksWrite(long epoch, long revision) {
        NetworkContents network = contents();
        if (network == null) {
            return false;
        }
        int copyComparedToWrite = Long.compareUnsigned(network.revision(), revision);
        return network.epoch() != epoch || copyComparedToWrite < 0;
    }

    // A header that is missing or not a number counts as no header: the write itself already succeeded.
    private static @Nullable Long headerNumber(Map<String, List<String>> headers, String name) {
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

    // Returns false when the thread was interrupted; the interrupt stays set for the caller.
    private static boolean sleep(Duration duration) {
        try {
            Thread.sleep(duration);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
