package app.simplecloud.api.internal.event;

import app.simplecloud.api.event.EventApi;
import app.simplecloud.api.event.blueprint.BlueprintEventApi;
import app.simplecloud.api.event.group.GroupEventApi;
import app.simplecloud.api.event.persistentserver.PersistentServerEventApi;
import app.simplecloud.api.event.player.PlayerEventApi;
import app.simplecloud.api.event.server.ServerEventApi;
import app.simplecloud.api.internal.event.blueprint.BlueprintEventApiImpl;
import app.simplecloud.api.internal.event.group.GroupEventApiImpl;
import app.simplecloud.api.internal.event.persistentserver.PersistentServerEventApiImpl;
import app.simplecloud.api.internal.event.player.PlayerEventApiImpl;
import app.simplecloud.api.internal.event.server.ServerEventApiImpl;
import app.simplecloud.api.player.PlayerApi;
import io.nats.client.Connection;

public class EventApiImpl implements EventApi {

    private final GroupEventApi groupEventApi;
    private final ServerEventApi serverEventApi;
    private final PersistentServerEventApi persistentServerEventApi;
    private final BlueprintEventApi blueprintEventApi;
    private final PlayerEventApi playerEventApi;

    public EventApiImpl(Connection natsClient, String networkId, PlayerApi playerApi) {
        EventSubscriber events = new EventSubscriber(natsClient, networkId);
        this.groupEventApi = new GroupEventApiImpl(events);
        this.serverEventApi = new ServerEventApiImpl(events);
        this.persistentServerEventApi = new PersistentServerEventApiImpl(events);
        this.blueprintEventApi = new BlueprintEventApiImpl(events);
        this.playerEventApi = new PlayerEventApiImpl(events, natsClient, playerApi);
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

    @Override
    public PlayerEventApi player() {
        return playerEventApi;
    }

}

