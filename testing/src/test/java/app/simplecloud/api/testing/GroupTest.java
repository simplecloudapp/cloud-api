package app.simplecloud.api.testing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.simplecloud.api.CloudApiException;
import app.simplecloud.api.event.group.GroupCreatedEvent;
import app.simplecloud.api.event.group.GroupDeletedEvent;
import app.simplecloud.api.event.server.ServerStoppedEvent;
import app.simplecloud.api.group.CreateGroupRequest;
import app.simplecloud.api.group.Group;
import app.simplecloud.api.group.GroupQuery;
import app.simplecloud.api.group.GroupServerType;
import app.simplecloud.api.group.UpdateGroupRequest;
import app.simplecloud.api.testing.group.TestGroup;
import java.util.List;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;

@CloudTest
class GroupTest {

    @Test
    void createdGroupIsVisibleThroughApi(TestCloud cloud) {
        var created = cloud.track(GroupCreatedEvent.class);

        TestGroup lobby = cloud.createGroup(CreateGroupRequest.builder()
                .name("lobby")
                .maxPlayers(50)
                .tags(List.of("hub"))
                .build());

        Group fetched = cloud.api().group().getGroupByName("lobby").join();
        assertEquals(lobby.getServerGroupId(), fetched.getServerGroupId());
        assertEquals(50, fetched.getMaxPlayers());
        assertEquals(GroupServerType.SERVER, fetched.getType());
        assertEquals("lobby", created.single().getGroup().getName());
        assertEquals(cloud.networkId(), created.single().getNetworkId());
    }

    @Test
    void duplicateNameFailsWithConflict(TestCloud cloud) {
        cloud.createGroup("lobby");

        CompletionException exception = assertThrows(
                CompletionException.class,
                () -> cloud.api()
                        .group()
                        .createGroup(new CreateGroupRequest("lobby"))
                        .join());

        CloudApiException cause = assertInstanceOf(CloudApiException.class, exception.getCause());
        assertEquals(CloudApiException.Reason.CONFLICT, cause.getReason());
    }

    @Test
    void missingGroupFailsWithNotFound(TestCloud cloud) {
        CompletionException exception = assertThrows(
                CompletionException.class,
                () -> cloud.api().group().getGroupByName("missing").join());

        CloudApiException cause = assertInstanceOf(CloudApiException.class, exception.getCause());
        assertEquals(CloudApiException.Reason.NOT_FOUND, cause.getReason());
        assertTrue(cloud.api().group().findGroupByName("missing").join().isEmpty());
    }

    @Test
    void handleReflectsUpdatesWhileSnapshotStaysFrozen(TestCloud cloud) {
        TestGroup lobby = cloud.createGroup("lobby");
        Group before = cloud.api().group().getGroupByName("lobby").join();

        lobby.update(UpdateGroupRequest.builder().maxPlayers(100).build()).setProperty("motd", "hello");

        assertEquals(100, lobby.getMaxPlayers());
        assertEquals("hello", lobby.getProperties().get("motd"));
        assertEquals(null, before.getMaxPlayers());
        assertTrue(before.getProperties().isEmpty());
    }

    @Test
    void queryFiltersByTypeAndTag(TestCloud cloud) {
        cloud.createGroup(CreateGroupRequest.builder()
                .name("proxy")
                .type(GroupServerType.PROXY)
                .build());
        cloud.createGroup(
                CreateGroupRequest.builder().name("lobby").tags(List.of("hub")).build());
        cloud.createGroup("bedwars");

        List<Group> proxies = cloud.api()
                .group()
                .getAllGroups(GroupQuery.create().filterByType(GroupServerType.PROXY))
                .join();
        List<Group> hubs = cloud.api()
                .group()
                .getAllGroups(GroupQuery.create().filterByTag("hub"))
                .join();

        assertEquals(List.of("proxy"), proxies.stream().map(Group::getName).toList());
        assertEquals(List.of("lobby"), hubs.stream().map(Group::getName).toList());
    }

    @Test
    void deleteStopsServersAndPublishesEvents(TestCloud cloud) {
        TestGroup lobby = cloud.createGroup("lobby");
        lobby.startServer();
        lobby.startServer();
        var stopped = cloud.track(ServerStoppedEvent.class);
        var deleted = cloud.track(GroupDeletedEvent.class);

        lobby.delete();

        assertEquals(2, stopped.count());
        assertEquals("lobby", deleted.single().getName());
        assertFalse(lobby.exists());
        assertTrue(cloud.api().server().getAllServers().join().isEmpty());
    }
}
