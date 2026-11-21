package app.simplecloud.api.testing;

import app.simplecloud.api.CloudApi;
import app.simplecloud.api.group.CreateGroupRequest;
import app.simplecloud.api.persistentserver.CreatePersistentServerRequest;
import app.simplecloud.api.server.Server;
import app.simplecloud.api.testing.controller.TestController;
import app.simplecloud.api.testing.event.EventTracker;
import app.simplecloud.api.testing.group.TestGroup;
import app.simplecloud.api.testing.internal.TestCloudImpl;
import app.simplecloud.api.testing.network.TestNetwork;
import app.simplecloud.api.testing.persistentserver.TestPersistentServer;
import app.simplecloud.api.testing.player.TestPlayer;
import app.simplecloud.api.testing.server.TestServer;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

public interface TestCloud extends AutoCloseable {

    String DEFAULT_NETWORK_ID = "test-network";

    static TestCloud create() {
        return create(DEFAULT_NETWORK_ID);
    }

    static TestCloud create(String networkId) {
        return new TestCloudImpl(networkId);
    }

    String networkId();

    CloudApi api();

    CloudApi newApi();

    CloudApi newApi(Server currentServer);

    void runAs(@Nullable Server server);

    TestGroup createGroup(String name);

    TestGroup createGroup(CreateGroupRequest request);

    Optional<TestGroup> group(String name);

    List<TestGroup> groups();

    Optional<TestServer> server(String name);

    List<TestServer> servers();

    TestPersistentServer createPersistentServer(String name);

    TestPersistentServer createPersistentServer(CreatePersistentServerRequest request);

    Optional<TestPersistentServer> persistentServer(String name);

    List<TestPersistentServer> persistentServers();

    TestPlayer createPlayer(String name);

    TestPlayer createPlayer(UUID uniqueId, String name);

    Optional<TestPlayer> player(String name);

    Optional<TestPlayer> player(UUID uniqueId);

    List<TestPlayer> players();

    void publish(Object event);

    <E> EventTracker<E> track(Class<E> type);

    <E> EventTracker<E> track(Class<E> type, Predicate<? super E> filter);

    void asyncEvents(boolean async);

    TestController controller();

    TestNetwork network();

    Instant now();

    void advanceTime(Duration duration);

    @Override
    void close();
}
