package app.simplecloud.api.testing;

import app.simplecloud.api.CloudApi;
import app.simplecloud.api.group.CreateGroupRequest;
import app.simplecloud.api.server.Server;
import app.simplecloud.api.testing.controller.TestController;
import app.simplecloud.api.testing.event.EventCollector;
import app.simplecloud.api.testing.group.TestGroup;
import app.simplecloud.api.testing.internal.TestCloudImpl;
import app.simplecloud.api.testing.server.TestServer;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
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

    TestGroup createGroup(String name, Consumer<CreateGroupRequest.Builder> configurer);

    Optional<TestGroup> group(String name);

    List<TestGroup> groups();

    Optional<TestServer> server(String name);

    List<TestServer> servers();

    void publish(Object event);

    <E> EventCollector<E> collect(Class<E> type);

    <E> EventCollector<E> collect(Class<E> type, Predicate<? super E> filter);

    void asyncEvents(boolean async);

    TestController controller();

    Instant now();

    void advanceTime(Duration duration);

    @Override
    void close();
}
