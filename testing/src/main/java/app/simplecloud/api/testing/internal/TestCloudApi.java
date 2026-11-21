package app.simplecloud.api.testing.internal;

import app.simplecloud.api.CloudApi;
import app.simplecloud.api.cache.QueryCache;
import app.simplecloud.api.event.EventApi;
import app.simplecloud.api.group.GroupApi;
import app.simplecloud.api.internal.cache.NoOpQueryCache;
import app.simplecloud.api.persistentserver.PersistentServerApi;
import app.simplecloud.api.player.PlayerApi;
import app.simplecloud.api.server.ServerApi;
import app.simplecloud.api.testing.internal.event.TestEventApi;
import app.simplecloud.api.testing.internal.group.TestGroupApi;
import app.simplecloud.api.testing.internal.player.TestPlayerApi;
import app.simplecloud.api.testing.internal.server.TestServerApi;
import org.jetbrains.annotations.Nullable;

final class TestCloudApi implements CloudApi {

    private final CloudState cloud;
    private final GroupApi groupApi;
    private final ServerApi serverApi;
    private final PlayerApi playerApi;
    private final EventApi eventApi;
    private final QueryCache cache = new NoOpQueryCache();
    private volatile @Nullable String currentServerId;

    TestCloudApi(CloudState cloud, @Nullable String currentServerId) {
        this.cloud = cloud;
        this.currentServerId = currentServerId;
        this.groupApi = cloud.network().wrap(GroupApi.class, new TestGroupApi(cloud));
        this.serverApi = cloud.network().wrap(ServerApi.class, new TestServerApi(cloud, () -> this.currentServerId));
        this.playerApi = cloud.network().wrap(PlayerApi.class, new TestPlayerApi(cloud, () -> this.playerApi));
        this.eventApi = new TestEventApi(cloud.bus());
    }

    void currentServerId(@Nullable String currentServerId) {
        this.currentServerId = currentServerId;
    }

    @Override
    public GroupApi group() {
        return groupApi;
    }

    @Override
    public ServerApi server() {
        return serverApi;
    }

    @Override
    public PersistentServerApi persistentServer() {
        throw new UnsupportedOperationException("Persistent servers are not implemented yet");
    }

    @Override
    public EventApi event() {
        return eventApi;
    }

    @Override
    public PlayerApi player() {
        return playerApi;
    }

    @Override
    public String getNetworkId() {
        return cloud.networkId();
    }

    @Override
    public QueryCache cache() {
        return cache;
    }
}
