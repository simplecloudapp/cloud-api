package app.simplecloud.api.testing;

import app.simplecloud.api.group.GroupStartQueue;
import app.simplecloud.api.group.GroupStartQueueItemStatus;
import app.simplecloud.api.testing.group.TestGroup;
import app.simplecloud.api.testing.server.TestServer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        assertEquals(2, queue.getQueuedStarts());
        assertEquals(2, queue.findByServerGroupName("lobby").getStarts().size());
        assertTrue(lobby.getServers().isEmpty());

        List<TestServer> started = cloud.controller().processStartQueue();

        assertEquals(2, started.size());
        assertEquals(0, cloud.controller().startQueue().getTotalStarts());
    }

    @Test
    void failedStartsStayInQueue(TestCloud cloud) {
        cloud.controller().autoStart(false);
        TestGroup lobby = cloud.createGroup("lobby");
        cloud.api().group().requestServerStart(lobby).join();

        cloud.controller().failQueuedStarts("lobby", "no serverhost available");
        cloud.controller().processStartQueue();

        GroupStartQueue queue = cloud.controller().startQueue();
        assertEquals(1, queue.getFailedStarts());
        assertEquals(GroupStartQueueItemStatus.FAILED, queue.getItems().getFirst().getStarts().getFirst().getStatus());
        assertTrue(lobby.getServers().isEmpty());
    }

    @Test
    void clearRemovesQueuedStarts(TestCloud cloud) {
        cloud.controller().autoStart(false);
        TestGroup lobby = cloud.createGroup("lobby");
        cloud.api().group().requestServerStart(lobby).join();

        cloud.api().group().clearServerStartQueue(lobby).join();

        assertEquals(0, cloud.controller().startQueue().getTotalStarts());
    }
}
