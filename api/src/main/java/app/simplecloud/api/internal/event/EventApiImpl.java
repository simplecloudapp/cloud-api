package app.simplecloud.api.internal.event;

import app.simplecloud.api.event.blueprint.BlueprintEventApi;
import app.simplecloud.api.event.EventApi;
import app.simplecloud.api.event.group.GroupEventApi;
import app.simplecloud.api.event.persistentserver.PersistentServerEventApi;
import app.simplecloud.api.event.server.ServerEventApi;
import app.simplecloud.api.internal.event.blueprint.BlueprintEventApiImpl;
import app.simplecloud.api.internal.event.group.GroupEventApiImpl;
import app.simplecloud.api.internal.event.persistentserver.PersistentServerEventApiImpl;
import app.simplecloud.api.internal.event.server.ServerEventApiImpl;
import io.nats.client.Connection;

public class EventApiImpl implements EventApi {

    private final Connection natsClient;
    private final String networkId;
    private final GroupEventApi groupEventApi;
    private final ServerEventApi serverEventApi;
    private final PersistentServerEventApi persistentServerEventApi;
    private final BlueprintEventApi blueprintEventApi;

    public EventApiImpl(Connection natsClient, String networkId) {
        this.natsClient = natsClient;
        this.networkId = networkId;
        this.groupEventApi = new GroupEventApiImpl(natsClient, networkId);
        this.serverEventApi = new ServerEventApiImpl(natsClient, networkId);
        this.persistentServerEventApi = new PersistentServerEventApiImpl(natsClient, networkId);
        this.blueprintEventApi = new BlueprintEventApiImpl(natsClient, networkId);
    }

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
        return blueprintEventApi;
    }

}

