package app.simplecloud.api.internal.cache;

import app.simplecloud.api.cache.QueryKey;
import app.simplecloud.api.event.EventApi;
import app.simplecloud.api.event.Subscription;
import app.simplecloud.api.event.blueprint.BlueprintEventApi;
import app.simplecloud.api.event.group.GroupEventApi;
import app.simplecloud.api.event.persistentserver.PersistentServerEventApi;
import app.simplecloud.api.event.server.ServerEventApi;
import app.simplecloud.api.event.server.ServerUpdatedEvent;
import app.simplecloud.api.server.Server;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CacheEventListenerTest {

    private static final Subscription NO_OP_SUBSCRIPTION = () -> {
    };

    @Test
    void shutdown_ignoresEventCallbackAlreadyQueuedByDispatcher() {
        RecordingCache cache = new RecordingCache();
        RecordingEventApi eventApi = new RecordingEventApi();
        CacheEventListener listener = new CacheEventListener(cache, eventApi, 50);

        listener.shutdown();

        assertDoesNotThrow(() -> eventApi.serverUpdated.accept(serverUpdatedEvent("server-1")));
        assertTrue(cache.invalidations.isEmpty());
        assertTrue(cache.patternInvalidations.isEmpty());
    }

    @Test
    void shutdown_flushesInvalidationsQueuedBeforeShutdown() {
        RecordingCache cache = new RecordingCache();
        RecordingEventApi eventApi = new RecordingEventApi();
        CacheEventListener listener = new CacheEventListener(cache, eventApi, 60_000);

        eventApi.serverUpdated.accept(serverUpdatedEvent("server-1"));
        listener.shutdown();

        assertTrue(cache.invalidations.contains(QueryKey.of("server", "server-1")));
        assertTrue(cache.patternInvalidations.contains(QueryKey.of("servers")));
    }

    private static ServerUpdatedEvent serverUpdatedEvent(String serverId) {
        return new ServerUpdatedEvent() {
            @Override
            public String getNetworkId() {
                return "test";
            }

            @Override
            public String getServerId() {
                return serverId;
            }

            @Override
            public String getServerGroupId() {
                return "group-1";
            }

            @Override
            public Server getServer() {
                return null;
            }

            @Override
            public String getTimestamp() {
                return "";
            }
        };
    }

    private static final class RecordingCache extends NoOpQueryCache {
        private final List<QueryKey> invalidations = new CopyOnWriteArrayList<>();
        private final List<QueryKey> patternInvalidations = new CopyOnWriteArrayList<>();

        @Override
        public void invalidate(QueryKey key) {
            invalidations.add(key);
        }

        @Override
        public void invalidateAll(QueryKey keyPattern) {
            patternInvalidations.add(keyPattern);
        }
    }

    private static final class RecordingEventApi implements EventApi {
        private Consumer<ServerUpdatedEvent> serverUpdated;
        private final ServerEventApi serverEventApi = eventProxy(ServerEventApi.class, (methodName, handler) -> {
            if ("onUpdated".equals(methodName)) {
                serverUpdated = cast(handler);
            }
        });
        private final GroupEventApi groupEventApi = eventProxy(GroupEventApi.class, (methodName, handler) -> {
        });
        private final PersistentServerEventApi persistentServerEventApi =
                eventProxy(PersistentServerEventApi.class, (methodName, handler) -> {
                });

        @Override
        public GroupEventApi group() {
            return groupEventApi;
        }

        @Override
        public ServerEventApi server() {
            return serverEventApi;
        }

        @Override
        public PersistentServerEventApi persistentServer() {
            return persistentServerEventApi;
        }

        @Override
        public BlueprintEventApi blueprint() {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T eventProxy(Class<T> eventApiType, HandlerRecorder recorder) {
        return (T) Proxy.newProxyInstance(
                eventApiType.getClassLoader(),
                new Class<?>[]{eventApiType},
                (proxy, method, args) -> {
                    recorder.record(method.getName(), args[0]);
                    return NO_OP_SUBSCRIPTION;
                }
        );
    }

    @SuppressWarnings("unchecked")
    private static <T> Consumer<T> cast(Object handler) {
        return (Consumer<T>) handler;
    }

    @FunctionalInterface
    private interface HandlerRecorder {
        void record(String methodName, Object handler);
    }
}
