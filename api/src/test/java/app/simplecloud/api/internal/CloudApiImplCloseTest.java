package app.simplecloud.api.internal;

import app.simplecloud.api.CloudApiOptions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CloudApiImplCloseTest {

    private static final String UNREACHABLE_NATS_URL = "nats://127.0.0.1:1";
    private static final Duration TASK_DURATION = Duration.ofSeconds(30);
    private static final Duration MAX_CLOSE_DURATION = Duration.ofSeconds(3);

    @Test
    void closeStopsWaitingForATaskThatRunsPastTheBudget() throws Exception {
        CloudApiImpl cloudApi = new CloudApiImpl(CloudApiOptions.builder()
                .natsUrl(UNREACHABLE_NATS_URL)
                .disableCache()
                .build());
        CountDownLatch taskStarted = new CountDownLatch(1);
        cloudApi.getExecutor().execute(() -> {
            taskStarted.countDown();
            try {
                Thread.sleep(TASK_DURATION);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        assertTrue(taskStarted.await(5, TimeUnit.SECONDS));

        long start = System.nanoTime();
        cloudApi.close();
        Duration closeDuration = Duration.ofNanos(System.nanoTime() - start);

        assertTrue(closeDuration.compareTo(MAX_CLOSE_DURATION) < 0, "close took " + closeDuration);
    }
}
