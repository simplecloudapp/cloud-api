package app.simplecloud.api.testing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.simplecloud.api.CloudApiException;
import app.simplecloud.api.group.GroupApi;
import app.simplecloud.api.server.Server;
import app.simplecloud.api.server.ServerApi;
import app.simplecloud.api.testing.server.TestServer;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;

@CloudTest
class NetworkTest {

    @Test
    void failNextFailsOnlyOnce(TestCloud cloud) {
        TestServer server = cloud.createGroup("lobby").startServer();
        cloud.network().failNext(ServerApi.class, "stopServer");

        CompletionException exception = assertThrows(
                CompletionException.class,
                () -> cloud.api().server().stopServer(server.getServerId()).join());

        CloudApiException cause = assertInstanceOf(CloudApiException.class, exception.getCause());
        assertEquals(CloudApiException.Reason.UNAVAILABLE, cause.getReason());
        assertTrue(server.isRunning());

        cloud.api().server().stopServer(server.getServerId()).join();
        assertFalse(server.isRunning());
    }

    @Test
    void failAlwaysFailsUntilCleared(TestCloud cloud) {
        IllegalStateException error = new IllegalStateException("controller down");
        cloud.network().failAlways(GroupApi.class, "getAllGroups", error);

        for (int i = 0; i < 3; i++) {
            CompletionException exception = assertThrows(
                    CompletionException.class,
                    () -> cloud.api().group().getAllGroups().join());
            assertSame(error, exception.getCause());
        }

        cloud.network().clear();
        assertTrue(cloud.api().group().getAllGroups().join().isEmpty());
    }

    @Test
    void failuresOnlyAffectTheMatchingMethod(TestCloud cloud) {
        TestServer server = cloud.createGroup("lobby").startServer();
        cloud.network().failAlways(ServerApi.class, "stopServer");

        Server fetched =
                cloud.api().server().getServerById(server.getServerId()).join();

        assertEquals(server.getServerId(), fetched.getServerId());
    }

    @Test
    void failuresDoNotAffectTestHandles(TestCloud cloud) {
        TestServer server = cloud.createGroup("lobby").startServer();
        cloud.network().failAlways(ServerApi.class, "stopServer");

        server.stop();

        assertFalse(server.isRunning());
    }

    @Test
    void delayCompletesLater(TestCloud cloud) {
        cloud.createGroup("lobby").startServer();
        cloud.network().delay(ServerApi.class, "getAllServers", Duration.ofMillis(200));

        CompletableFuture<List<Server>> future = cloud.api().server().getAllServers();

        assertFalse(future.isDone());
        assertEquals(1, future.join().size());
    }

    @Test
    void unknownMethodIsRejected(TestCloud cloud) {
        assertThrows(IllegalArgumentException.class, () -> cloud.network().failNext(ServerApi.class, "stopServr"));
    }
}
