package app.simplecloud.api.testing.internal;

import app.simplecloud.api.CloudApi;
import app.simplecloud.api.group.CreateGroupRequest;
import app.simplecloud.api.persistentserver.CreatePersistentServerRequest;
import app.simplecloud.api.server.Server;
import app.simplecloud.api.testing.TestCloud;
import app.simplecloud.api.testing.controller.TestController;
import app.simplecloud.api.testing.event.EventTracker;
import app.simplecloud.api.testing.group.TestGroup;
import app.simplecloud.api.testing.internal.event.EventTrackerImpl;
import app.simplecloud.api.testing.internal.event.SimulatedEvents;
import app.simplecloud.api.testing.network.TestNetwork;
import app.simplecloud.api.testing.persistentserver.TestPersistentServer;
import app.simplecloud.api.testing.player.TestPlayer;
import app.simplecloud.api.testing.server.TestServer;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

public final class TestCloudImpl implements TestCloud {

    private final CloudState cloud;
    private final TestCloudApi api;
    private final TestController controller;

    public TestCloudImpl(String networkId) {
        this.cloud = new CloudState(networkId);
        this.api = new TestCloudApi(cloud, null);
        this.controller = new TestControllerImpl(cloud);
    }

    @Override
    public String networkId() {
        return cloud.networkId();
    }

    @Override
    public CloudApi api() {
        return api;
    }

    @Override
    public CloudApi newApi() {
        return new TestCloudApi(cloud, null);
    }

    @Override
    public CloudApi newApi(Server currentServer) {
        return new TestCloudApi(cloud, currentServer.getServerId());
    }

    @Override
    public void runAs(@Nullable Server server) {
        api.currentServerId(server == null ? null : server.getServerId());
    }

    @Override
    public TestGroup createGroup(String name) {
        return createGroup(new CreateGroupRequest(name));
    }

    @Override
    public TestGroup createGroup(CreateGroupRequest request) {
        return cloud.handle(cloud.createGroup(Objects.requireNonNull(request, "request")));
    }

    @Override
    public Optional<TestGroup> group(String name) {
        return cloud.read(() -> cloud.groupByName(name).<TestGroup>map(cloud::handle));
    }

    @Override
    public List<TestGroup> groups() {
        return cloud.read(() -> cloud.groups(null).stream()
                .<TestGroup>map(cloud::handle)
                .toList());
    }

    @Override
    public Optional<TestServer> server(String name) {
        return cloud.read(() -> cloud.serverByName(name).<TestServer>map(cloud::handle));
    }

    @Override
    public List<TestServer> servers() {
        return cloud.read(() -> cloud.servers(null).stream()
                .<TestServer>map(cloud::handle)
                .toList());
    }

    @Override
    public TestPersistentServer createPersistentServer(String name) {
        return createPersistentServer(CreatePersistentServerRequest.builder().name(name).build());
    }

    @Override
    public TestPersistentServer createPersistentServer(CreatePersistentServerRequest request) {
        return cloud.handle(cloud.createPersistentServer(request));
    }

    @Override
    public Optional<TestPersistentServer> persistentServer(String name) {
        return cloud.read(() -> cloud.persistentServerByName(name).<TestPersistentServer>map(cloud::handle));
    }

    @Override
    public List<TestPersistentServer> persistentServers() {
        return cloud.read(() -> cloud.persistentServers(null).stream()
                .<TestPersistentServer>map(cloud::handle)
                .toList());
    }

    @Override
    public TestPlayer createPlayer(String name) {
        Objects.requireNonNull(name, "name");
        return createPlayer(UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8)), name);
    }

    @Override
    public TestPlayer createPlayer(UUID uniqueId, String name) {
        return cloud.handle(cloud.createPlayer(uniqueId, name));
    }

    @Override
    public Optional<TestPlayer> player(String name) {
        return cloud.read(() -> cloud.playerByName(name).<TestPlayer>map(cloud::handle));
    }

    @Override
    public Optional<TestPlayer> player(UUID uniqueId) {
        return cloud.read(() -> cloud.playerById(uniqueId).<TestPlayer>map(cloud::handle));
    }

    @Override
    public List<TestPlayer> players() {
        return cloud.read(() -> cloud.players(null).stream()
                .<TestPlayer>map(cloud::handle)
                .toList());
    }

    @Override
    public void publish(Object event) {
        Objects.requireNonNull(event, "event");
        if (event instanceof SimulatedEvents.Stampable stampable) {
            event = stampable.stamp(cloud.networkId(), cloud.timestamp());
        }
        cloud.bus().publish(event);
    }

    @Override
    public <E> EventTracker<E> track(Class<E> type) {
        return track(type, event -> true);
    }

    @Override
    public <E> EventTracker<E> track(Class<E> type, Predicate<? super E> filter) {
        return EventTrackerImpl.subscribe(cloud.bus(), type, filter);
    }

    @Override
    public void asyncEvents(boolean async) {
        cloud.bus().async(async);
    }

    @Override
    public TestController controller() {
        return controller;
    }

    @Override
    public TestNetwork network() {
        return cloud.network();
    }

    @Override
    public Instant now() {
        return cloud.now();
    }

    @Override
    public void advanceTime(Duration duration) {
        cloud.advanceTime(duration);
    }

    @Override
    public void close() {
        cloud.bus().close();
    }
}
