package app.simplecloud.api.internal.cache;

import app.simplecloud.api.cache.QueryKey;
import app.simplecloud.api.event.EventApi;
import app.simplecloud.api.event.Subscription;
import app.simplecloud.api.event.blueprint.BlueprintEventApi;
import app.simplecloud.api.event.group.GroupEventApi;
import app.simplecloud.api.event.persistentserver.PersistentServerEventApi;
import app.simplecloud.api.event.player.PlayerEventApi;
import app.simplecloud.api.event.server.ServerEventApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CacheEventListenerTest {

    private static final long LONG_WINDOW_MS = 60_000;
    private static final long SHORT_WINDOW_MS = 50;

    private static final Set<QueryKey> SERVER_LIST_PATTERNS = Set.of(
            QueryKey.of("servers"),
            QueryKey.of("server", "numerical")
    );
    private static final Set<QueryKey> GROUP_LIST_PATTERNS = Set.of(
            QueryKey.of("groups"),
            QueryKey.of("group", "name")
    );
    private static final Set<QueryKey> PERSISTENT_SERVER_LIST_PATTERNS = Set.of(
            QueryKey.of("persistentServers"),
            QueryKey.of("persistentServer", "name")
    );

    /**
     * Fired events answer every getter with the getter's own name, so the expected key names the
     * getter the listener must read the ID from.
     */
    static Stream<Arguments> eventsAndTheirInvalidations() {
        Set<QueryKey> serverKey = Set.of(QueryKey.of("server", "getServerId"));
        Set<QueryKey> groupKey = Set.of(QueryKey.of("group", "getServerGroupId"));
        Set<QueryKey> persistentServerKey = Set.of(QueryKey.of("persistentServer", "getPersistentServerId"));
        return Stream.of(
                Arguments.of("server.onStarted", serverKey, SERVER_LIST_PATTERNS),
                Arguments.of("server.onStopped", serverKey, SERVER_LIST_PATTERNS),
                Arguments.of("server.onStateChanged", serverKey, SERVER_LIST_PATTERNS),
                Arguments.of("server.onDeleted", serverKey, SERVER_LIST_PATTERNS),
                Arguments.of("server.onUpdated", serverKey, SERVER_LIST_PATTERNS),
                Arguments.of("group.onCreated", groupKey, GROUP_LIST_PATTERNS),
                Arguments.of("group.onUpdated", groupKey, GROUP_LIST_PATTERNS),
                Arguments.of("group.onDeleted", groupKey, GROUP_LIST_PATTERNS),
                Arguments.of("persistentServer.onCreated", persistentServerKey, PERSISTENT_SERVER_LIST_PATTERNS),
                Arguments.of("persistentServer.onStarted", persistentServerKey, PERSISTENT_SERVER_LIST_PATTERNS),
                Arguments.of("persistentServer.onStopped", persistentServerKey, PERSISTENT_SERVER_LIST_PATTERNS),
                Arguments.of("persistentServer.onUpdated", persistentServerKey, PERSISTENT_SERVER_LIST_PATTERNS),
                Arguments.of("persistentServer.onDeleted", persistentServerKey, PERSISTENT_SERVER_LIST_PATTERNS)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("eventsAndTheirInvalidations")
    void event_invalidatesItsEntityAndLists(String handler, Set<QueryKey> expectedKeys, Set<QueryKey> expectedPatterns) {
        RecordingCache cache = new RecordingCache();
        FakeEventApi eventApi = new FakeEventApi();
        CacheEventListener listener = new CacheEventListener(cache, eventApi, LONG_WINDOW_MS);

        eventApi.fire(handler);
        listener.shutdown();

        assertEquals(expectedKeys, Set.copyOf(cache.invalidations));
        assertEquals(expectedPatterns, Set.copyOf(cache.patternInvalidations));
    }

    @Test
    void constructor_subscribesToEveryCacheRelevantEvent() {
        FakeEventApi eventApi = new FakeEventApi();

        new CacheEventListener(new RecordingCache(), eventApi, LONG_WINDOW_MS);

        Set<String> expectedHandlers = eventsAndTheirInvalidations()
                .map(arguments -> (String) arguments.get()[0])
                .collect(Collectors.toSet());
        assertEquals(expectedHandlers, eventApi.handlers.keySet());
    }

    @Test
    void eventsInsideOneWindow_areAppliedOnce() throws Exception {
        RecordingCache cache = new RecordingCache();
        FakeEventApi eventApi = new FakeEventApi();
        CacheEventListener listener = new CacheEventListener(cache, eventApi, SHORT_WINDOW_MS);

        eventApi.fire("server.onUpdated");
        eventApi.fire("server.onUpdated");
        eventApi.fire("server.onUpdated");
        // The batch applies patterns before keys, so a recorded key means the whole batch is done.
        awaitTrue(() -> !cache.invalidations.isEmpty(), "batch should be applied after the window");
        listener.shutdown();

        assertEquals(List.of(QueryKey.of("server", "getServerId")), cache.invalidations);
        assertEquals(SERVER_LIST_PATTERNS.size(), cache.patternInvalidations.size());
    }

    /**
     * Regression test: each event used to cancel and restart the timer, so events arriving
     * faster than the window kept the cache from ever being invalidated.
     */
    @Test
    void steadyEventStream_stillInvalidatesWhileEventsKeepArriving() throws Exception {
        RecordingCache cache = new RecordingCache();
        FakeEventApi eventApi = new FakeEventApi();
        CacheEventListener listener = new CacheEventListener(cache, eventApi, SHORT_WINDOW_MS);
        long eventIntervalMs = SHORT_WINDOW_MS / 5;
        long streamDurationMs = SHORT_WINDOW_MS * 10;

        long streamEndsAt = System.currentTimeMillis() + streamDurationMs;
        while (System.currentTimeMillis() < streamEndsAt) {
            eventApi.fire("server.onUpdated");
            Thread.sleep(eventIntervalMs);
        }
        boolean invalidatedDuringStream = !cache.invalidations.isEmpty();
        listener.shutdown();

        assertTrue(invalidatedDuringStream, "the cache must be invalidated while events keep arriving");
    }

    @Test
    void shutdown_flushesInvalidationsQueuedBeforeShutdown() {
        RecordingCache cache = new RecordingCache();
        FakeEventApi eventApi = new FakeEventApi();
        CacheEventListener listener = new CacheEventListener(cache, eventApi, LONG_WINDOW_MS);

        eventApi.fire("server.onUpdated");
        listener.shutdown();

        assertTrue(cache.invalidations.contains(QueryKey.of("server", "getServerId")));
        assertTrue(cache.patternInvalidations.contains(QueryKey.of("servers")));
    }

    @Test
    void shutdown_ignoresEventCallbackAlreadyQueuedByDispatcher() {
        RecordingCache cache = new RecordingCache();
        FakeEventApi eventApi = new FakeEventApi();
        CacheEventListener listener = new CacheEventListener(cache, eventApi, SHORT_WINDOW_MS);

        listener.shutdown();
        eventApi.fire("server.onUpdated");

        assertTrue(cache.invalidations.isEmpty());
        assertTrue(cache.patternInvalidations.isEmpty());
    }

    @Test
    void shutdown_closesEverySubscription() {
        FakeEventApi eventApi = new FakeEventApi();
        CacheEventListener listener = new CacheEventListener(new RecordingCache(), eventApi, LONG_WINDOW_MS);

        listener.shutdown();

        assertEquals(eventApi.handlers.size(), eventApi.closedSubscriptions.get());
    }

    @Test
    void shutdown_calledTwice_closesSubscriptionsOnce() {
        FakeEventApi eventApi = new FakeEventApi();
        CacheEventListener listener = new CacheEventListener(new RecordingCache(), eventApi, LONG_WINDOW_MS);

        listener.shutdown();
        listener.shutdown();

        assertEquals(eventApi.handlers.size(), eventApi.closedSubscriptions.get());
    }

    private static void awaitTrue(BooleanSupplier condition, String message) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 2_000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError(message);
            }
            Thread.sleep(5);
        }
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

    /**
     * Records every handler the listener registers under "entity.method", and fires them with an
     * event whose getters return their own method name.
     */
    private static final class FakeEventApi implements EventApi {
        private final Map<String, Consumer<Object>> handlers = new ConcurrentHashMap<>();
        private final AtomicInteger closedSubscriptions = new AtomicInteger();
        private final ServerEventApi server = recordingProxy(ServerEventApi.class, "server");
        private final GroupEventApi group = recordingProxy(GroupEventApi.class, "group");
        private final PersistentServerEventApi persistentServer =
                recordingProxy(PersistentServerEventApi.class, "persistentServer");

        void fire(String handlerName) {
            Consumer<Object> handler = handlers.get(handlerName);
            Object event = eventAnsweringGetterNames(handlerName);
            handler.accept(event);
        }

        private Object eventAnsweringGetterNames(String handlerName) {
            Method registration = registrationMethod(handlerName);
            ParameterizedType consumerType = (ParameterizedType) registration.getGenericParameterTypes()[0];
            Class<?> eventType = (Class<?>) consumerType.getActualTypeArguments()[0];
            return Proxy.newProxyInstance(
                    eventType.getClassLoader(),
                    new Class<?>[]{eventType},
                    (proxy, method, args) -> method.getName()
            );
        }

        private Method registrationMethod(String handlerName) {
            String[] parts = handlerName.split("\\.");
            Class<?> apiType = switch (parts[0]) {
                case "server" -> ServerEventApi.class;
                case "group" -> GroupEventApi.class;
                case "persistentServer" -> PersistentServerEventApi.class;
                default -> throw new IllegalArgumentException(handlerName);
            };
            for (Method method : apiType.getMethods()) {
                if (method.getName().equals(parts[1])) {
                    return method;
                }
            }
            throw new IllegalArgumentException(handlerName);
        }

        @SuppressWarnings("unchecked")
        private <T> T recordingProxy(Class<T> apiType, String entity) {
            Subscription subscription = closedSubscriptions::incrementAndGet;
            return (T) Proxy.newProxyInstance(
                    apiType.getClassLoader(),
                    new Class<?>[]{apiType},
                    (proxy, method, args) -> {
                        handlers.put(entity + "." + method.getName(), (Consumer<Object>) args[0]);
                        return subscription;
                    }
            );
        }

        @Override
        public ServerEventApi server() {
            return server;
        }

        @Override
        public GroupEventApi group() {
            return group;
        }

        @Override
        public PersistentServerEventApi persistentServer() {
            return persistentServer;
        }

        @Override
        public BlueprintEventApi blueprint() {
            return null;
        }

        @Override
        public PlayerEventApi player() {
            return null;
        }
    }
}
