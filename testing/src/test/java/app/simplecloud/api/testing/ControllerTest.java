package app.simplecloud.api.testing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.simplecloud.api.group.GroupStartQueue;
import app.simplecloud.api.group.GroupStartQueueItemStatus;
import app.simplecloud.api.testing.group.TestGroup;
import app.simplecloud.api.testing.server.TestServer;
import java.util.List;
import org.junit.jupiter.api.Test;

@CloudTest
class ControllerTest {

    @Test
    void requestServerStartStartsImmediatelyByDefault(TestCloud cloud) {
        TestGroup lobby = cloud.createGroup("lobby");

        cloud.api().group().requestServerStart(lobby).join();

        assertEquals(1, lobby.getServers().size());
    }

    @Test
    void queuedStartsWaitUntilProcessed(TestCloud cloud) {
        cloud.controller().autoStart(false);
        TestGroup lobby = cloud.createGroup("lobby");

        cloud.api().group().requestServerStart(lobby).join();
        cloud.api().group().requestServerStart(lobby).join();

        GroupStartQueue queue = cloud.api().group().getServerStartQueue().join();
        assertEquals(2, queue.queuedStarts());
        assertEquals(2, queue.findByServerGroupName("lobby").starts().size());
        assertTrue(lobby.getServers().isEmpty());

        List<TestServer> started = cloud.controller().processStartQueue();

        assertEquals(2, started.size());
        assertEquals(0, cloud.controller().startQueue().totalStarts());
    }

    @Test
    void failedStartsStayInQueue(TestCloud cloud) {
        cloud.controller().autoStart(false);
        TestGroup lobby = cloud.createGroup("lobby");
        cloud.api().group().requestServerStart(lobby).join();

        cloud.controller().failQueuedStarts("lobby", "no serverhost available");
        cloud.controller().processStartQueue();

        GroupStartQueue queue = cloud.controller().startQueue();
        assertEquals(1, queue.failedStarts());
        assertEquals(
                GroupStartQueueItemStatus.FAILED,
                queue.items().getFirst().starts().getFirst().status());
        assertTrue(lobby.getServers().isEmpty());
    }

    @Test
    void clearRemovesQueuedStarts(TestCloud cloud) {
        cloud.controller().autoStart(false);
        TestGroup lobby = cloud.createGroup("lobby");
        cloud.api().group().requestServerStart(lobby).join();

        cloud.api().group().clearServerStartQueue(lobby).join();

        assertEquals(0, cloud.controller().startQueue().totalStarts());
    }
}
