package app.simplecloud.api.platform.shared;

import app.simplecloud.api.CloudApi;
import app.simplecloud.api.server.ServerApi;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PlayerSynchronizerTest {

    @Test
    void publishesOnlyChangedCountsAndRetriesFailedWrites() {
        CloudApi cloudApi = mock(CloudApi.class);
        ServerApi serverApi = mock(ServerApi.class);
        when(cloudApi.server()).thenReturn(serverApi);
        when(serverApi.updateServerProperties(anyString(), anyMap()))
                .thenReturn(CompletableFuture.completedFuture(Map.of()));
        AtomicInteger onlineCount = new AtomicInteger(3);
        PlayerSynchronizer synchronizer = new PlayerSynchronizer(cloudApi, onlineCount::get);

        synchronizer.publishPlayerCount();
        synchronizer.publishPlayerCount();
        verify(serverApi, times(1)).updateServerProperties(anyString(), eq(Map.of("player-count", 3)));

        onlineCount.set(2);
        when(serverApi.updateServerProperties(anyString(), eq(Map.of("player-count", 2))))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("controller unavailable")))
                .thenReturn(CompletableFuture.completedFuture(Map.of()));
        synchronizer.publishPlayerCount();
        synchronizer.publishPlayerCount();
        synchronizer.publishPlayerCount();
        verify(serverApi, times(2)).updateServerProperties(anyString(), eq(Map.of("player-count", 2)));
    }
}
