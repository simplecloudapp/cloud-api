package app.simplecloud.api.testing;

import app.simplecloud.api.CloudApi;
import app.simplecloud.api.blueprint.Blueprint;
import app.simplecloud.api.blueprint.CreateBlueprintRequest;
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

/**
 * A simulated cloud network for tests.
 *
 * <p>{@link #api()} is what your code under test uses, it behaves like a real {@link CloudApi}.
 *
 * <pre>{@code
 * TestCloud cloud = TestCloud.create();
 *
 * var started = cloud.track(ServerStartedEvent.class);
 * TestServer lobby = cloud.createGroup("lobby").startServer();
 * cloud.createPlayer("Steve").join(lobby);
 *
 * assertEquals(1, started.count());
 * }</pre>
 */
public interface TestCloud extends AutoCloseable {

    /**
     * Network id used by {@link #create()}.
     */
    String DEFAULT_NETWORK_ID = "test-network";

    /**
     * Creates a new cloud with {@link #DEFAULT_NETWORK_ID}.
     */
    static TestCloud create() {
        return create(DEFAULT_NETWORK_ID);
    }

    /**
     * Creates a new cloud with the given network id.
     */
    static TestCloud create(String networkId) {
        return new TestCloudImpl(networkId);
    }

    /**
     * Returns the network id of this cloud.
     */
    String networkId();

    /**
     * Returns the api your code under test should use. Network failures from {@link #network()} apply here.
     */
    CloudApi api();

    /**
     * Creates another independent api instance on the same cloud.
     */
    CloudApi newApi();

    /**
     * Creates an api instance whose {@code getCurrentServer()} returns the given server.
     */
    CloudApi newApi(Server currentServer);

    /**
     * Sets the server {@link #api()} runs on, or {@code null} to unset it.
     */
    void runAs(@Nullable Server server);

    /**
     * Creates a group with default settings.
     */
    TestGroup createGroup(String name);

    /**
     * Creates a group from a full request.
     */
    TestGroup createGroup(CreateGroupRequest request);

    /**
     * Finds a group by name.
     */
    Optional<TestGroup> group(String name);

    /**
     * Returns all groups sorted by name.
     */
    List<TestGroup> groups();

    /**
     * Finds a running server by name, for example {@code lobby-1}.
     */
    Optional<TestServer> server(String name);

    /**
     * Returns all running servers.
     */
    List<TestServer> servers();

    /**
     * Creates a persistent server with default settings. It is not started.
     */
    TestPersistentServer createPersistentServer(String name);

    /**
     * Creates a persistent server from a full request. It is not started.
     */
    TestPersistentServer createPersistentServer(CreatePersistentServerRequest request);

    /**
     * Finds a persistent server by name.
     */
    Optional<TestPersistentServer> persistentServer(String name);

    /**
     * Returns all persistent servers sorted by name.
     */
    List<TestPersistentServer> persistentServers();

    /**
     * Creates a paper blueprint with default versions.
     */
    Blueprint createBlueprint(String name);

    /**
     * Creates a blueprint from a full request. A missing server URL is filled with a fake download link.
     */
    Blueprint createBlueprint(CreateBlueprintRequest request);

    /**
     * Finds a blueprint by name.
     */
    Optional<Blueprint> blueprint(String name);

    /**
     * Returns all blueprints, newest first.
     */
    List<Blueprint> blueprints();

    /**
     * Creates an offline player with the offline mode uuid of the name.
     */
    TestPlayer createPlayer(String name);

    /**
     * Creates an offline player with the given uuid.
     */
    TestPlayer createPlayer(UUID uniqueId, String name);

    /**
     * Finds a player by name, ignoring case.
     */
    Optional<TestPlayer> player(String name);

    /**
     * Finds a player by uuid.
     */
    Optional<TestPlayer> player(UUID uniqueId);

    /**
     * Returns all known players, online and offline.
     */
    List<TestPlayer> players();

    /**
     * Publishes an event to all subscribers without changing any state.
     */
    void publish(Object event);

    /**
     * Starts recording events of the given type.
     */
    <E> EventTracker<E> track(Class<E> type);

    /**
     * Starts recording events of the given type that match the filter.
     */
    <E> EventTracker<E> track(Class<E> type, Predicate<? super E> filter);

    /**
     * Delivers events on a separate thread instead of the calling one. Off by default.
     */
    void asyncEvents(boolean async);

    /**
     * Returns the simulated controller for start queue, scaling and start state.
     */
    TestController controller();

    /**
     * Returns the simulated network for failures and delays of api calls.
     */
    TestNetwork network();

    /**
     * Returns the current time of this cloud. It only moves with {@link #advanceTime(Duration)}.
     */
    Instant now();

    /**
     * Moves the clock forward, for example to let online time pass.
     */
    void advanceTime(Duration duration);

    @Override
    void close();
}
