package app.simplecloud.api.internal.cache;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import app.simplecloud.api.cache.CacheConfig;
import app.simplecloud.api.cache.QueryKey;
import app.simplecloud.api.internal.event.EventApiImpl;
import app.simplecloud.api.support.Await;
import app.simplecloud.api.support.NatsTest;
import app.simplecloud.api.support.TestNats;
import build.buf.gen.simplecloud.controller.v2.ServerGroupUpdatedEvent;
import io.nats.client.Connection;
import org.junit.jupiter.api.Test;

@NatsTest
class CacheEventListenerTest {

    @Test
    void groupUpdateEventInvalidatesThatGroupAndAllGroupLists() throws Exception {
        String networkId = TestNats.randomNetworkId();
        QueryKey updatedGroup = QueryKey.of("group", "group-1");
        QueryKey otherGroup = QueryKey.of("group", "group-2");
        QueryKey groupList = QueryKey.of("groups");
        QueryKey groupByName = QueryKey.of("group", "name", "Lobby");
        QueryCacheImpl cache = new QueryCacheImpl(CacheConfig.DEFAULT);

        try (Connection apiConnection = TestNats.connect();
                Connection controller = TestNats.connect()) {
            CacheEventListener listener =
                    new CacheEventListener(cache, new EventApiImpl(apiConnection, networkId, null), 10);
            apiConnection.flush(TestNats.TIMEOUT);
            cache.set(updatedGroup, "group-1");
            cache.set(otherGroup, "group-2");
            cache.set(groupList, "all groups");
            cache.set(groupByName, "Lobby");

            controller.publish(
                    networkId + ".event.group.updated",
                    ServerGroupUpdatedEvent.newBuilder()
                            .setServerGroupId("group-1")
                            .build()
                            .toByteArray());

            Await.until(() -> cache.get(updatedGroup) == null, "the updated group is invalidated");
            Await.until(
                    () -> cache.get(groupList) == null && cache.get(groupByName) == null,
                    "group lists are invalidated");
            assertNotNull(cache.get(otherGroup));
            listener.shutdown();
        } finally {
            cache.shutdown();
        }
    }
}
