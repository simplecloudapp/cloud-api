package app.simplecloud.api.internal;

import app.simplecloud.api.CloudApi;
import app.simplecloud.api.CloudApiOptions;
import app.simplecloud.api.internal.networkstate.NetworkViewShell;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkViewSharingTest {

    private static final String UNREACHABLE_URL = "127.0.0.1:1";
    /**
     * The name of the thread that sends a view's time ticks, followed by its network id. The thread runs from the
     * view's start until its close.
     */
    private static final String VIEW_THREAD_NAME_PREFIX = "simplecloud-network-view-";
    private static final Duration STOP_WAIT_LIMIT = Duration.ofSeconds(5);

    // Each test gets its own network, so an instance a failed test leaves open shares no view with the next.
    private final String networkId = UUID.randomUUID().toString();

    @Test
    void instancesWithEqualSettingsShareOneViewAndOthersGetTheirOwn() {
        CloudApiOptions sharedOptions = options(UnaryOperator.identity());
        CloudApiOptions otherCredential = options(builder -> builder.networkSecret("otherPassword"));
        CloudApiOptions otherController = options(builder -> builder.controllerUrl("http://127.0.0.2:1"));

        try (CloudApiImpl first = (CloudApiImpl) CloudApi.create(sharedOptions);
             CloudApiImpl second = (CloudApiImpl) CloudApi.create(sharedOptions);
             CloudApiImpl withOtherCredential = (CloudApiImpl) CloudApi.create(otherCredential);
             CloudApiImpl withOtherController = (CloudApiImpl) CloudApi.create(otherController)) {
            NetworkViewShell firstView = first.runtime().networkViewShell();

            assertSame(firstView, second.runtime().networkViewShell());
            assertNotSame(firstView, withOtherCredential.runtime().networkViewShell());
            assertNotSame(firstView, withOtherController.runtime().networkViewShell());
        }
    }

    @Test
    void aCredentialThatMayBeAJwtAndANetworkThatIsNoUuidGetNoView() {
        CloudApiOptions jwt = options(builder -> builder.networkSecret("header.payload.signature"));
        CloudApiOptions jsonCredential = options(builder -> builder.networkSecret("{\"token\":\"abc\"}"));
        CloudApiOptions namedNetwork = options(builder -> builder.networkId("default"));

        try (CloudApiImpl withJwt = (CloudApiImpl) CloudApi.create(jwt);
             CloudApiImpl withJsonCredential = (CloudApiImpl) CloudApi.create(jsonCredential);
             CloudApiImpl withNamedNetwork = (CloudApiImpl) CloudApi.create(namedNetwork)) {
            assertNull(withJwt.runtime().networkViewShell());
            assertNull(withJsonCredential.runtime().networkViewShell());
            assertNull(withNamedNetwork.runtime().networkViewShell());
        }
    }

    @Test
    void theViewStaysOpenUntilTheLastInstanceCloses() throws InterruptedException {
        CloudApiOptions sharedOptions = options(UnaryOperator.identity());
        CloudApiImpl platformPlugin = (CloudApiImpl) CloudApi.create(sharedOptions);
        CloudApiImpl otherPlugin = (CloudApiImpl) CloudApi.create(sharedOptions);
        NetworkViewShell sharedView = platformPlugin.runtime().networkViewShell();

        platformPlugin.close();
        CloudApiImpl reenabledPlatformPlugin = (CloudApiImpl) CloudApi.create(sharedOptions);
        NetworkViewShell viewOfReenabled = reenabledPlatformPlugin.runtime().networkViewShell();
        boolean runsWhileInUse = viewThreadRuns();
        otherPlugin.close();
        reenabledPlatformPlugin.close();
        boolean stoppedAfterLastClose = viewThreadStops();

        assertSame(sharedView, viewOfReenabled);
        assertTrue(runsWhileInUse, "the view runs while another instance uses it");
        assertTrue(stoppedAfterLastClose, "the last close closes the view");
        try (CloudApiImpl afterRestart = (CloudApiImpl) CloudApi.create(sharedOptions)) {
            assertNotSame(sharedView, afterRestart.runtime().networkViewShell());
        }
    }

    @Test
    void theLastCloseShutsDownTheDispatcherOfTheClientThatReportsWrites() {
        CloudApiImpl api = (CloudApiImpl) CloudApi.create(options(UnaryOperator.identity()));
        OkHttpClient reportingWrites = api.runtime().httpClient();

        api.close();

        assertTrue(reportingWrites.dispatcher().executorService().isShutdown());
    }

    private CloudApiOptions options(UnaryOperator<CloudApiOptions.Builder> customizer) {
        CloudApiOptions.Builder builder = CloudApiOptions.builder()
                .natsUrl("nats://" + UNREACHABLE_URL)
                .controllerUrl("http://" + UNREACHABLE_URL)
                .networkId(networkId)
                .networkSecret("networkPassword");
        return customizer.apply(builder).build();
    }

    private boolean viewThreadRuns() {
        String viewThreadName = VIEW_THREAD_NAME_PREFIX + networkId;
        Set<Thread> threads = Thread.getAllStackTraces().keySet();
        return threads.stream().anyMatch(thread -> viewThreadName.equals(thread.getName()));
    }

    /**
     * Waits for the view's thread to end: closing the view stops its timer, whose thread ends soon after.
     */
    private boolean viewThreadStops() throws InterruptedException {
        long deadline = System.nanoTime() + STOP_WAIT_LIMIT.toNanos();
        while (viewThreadRuns()) {
            if (System.nanoTime() > deadline) {
                return false;
            }
            Thread.sleep(10);
        }
        return true;
    }
}
