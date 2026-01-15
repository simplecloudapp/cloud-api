package app.simplecloud.api.internal;

import app.simplecloud.api.CloudApi;
import app.simplecloud.api.CloudApiOptions;
import app.simplecloud.api.event.EventApi;
import app.simplecloud.api.group.GroupApi;
import app.simplecloud.api.internal.event.EventApiImpl;
import app.simplecloud.api.internal.group.GroupApiImpl;
import app.simplecloud.api.internal.server.ServerApiImpl;
import app.simplecloud.api.internal.player.PlayerApiImpl;
import app.simplecloud.api.player.PlayerApi;
import app.simplecloud.api.server.ServerApi;
import io.nats.client.Connection;
import io.nats.client.Nats;
import io.nats.client.Options;

import java.io.IOException;

public class CloudApiImpl implements CloudApi {

    private final CloudApiOptions options;

    private final Connection natsClient;
    private final ServerApi serverApi;
    private final GroupApi groupApi;
    private final EventApi eventApi;
    private final PlayerApi playerApi;

    public CloudApiImpl(CloudApiOptions options) {
        this.options = options;

        try {
            this.natsClient = Nats.connect(
                    Options.builder()
                            .server(options.getNatsUrl())
                            .userInfo(options.getNetworkId(), options.getNetworkSecret())
                            .build()
            );
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }

        this.serverApi = new ServerApiImpl(options);
        this.groupApi = new GroupApiImpl(options);
        this.eventApi = new EventApiImpl(natsClient, options.getNetworkId());
        this.playerApi = new PlayerApiImpl(options, natsClient);
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
    public EventApi event() {
        return eventApi;
    }

    @Override
    public PlayerApi player() {
        return playerApi;
    }

    /**
     * Returns the underlying NATS connection.
     * This is for internal use by integration modules.
     *
     * @return the NATS connection
     */
    public Connection getNatsConnection() {
        return natsClient;
    }

    @Override
    public String getNetworkId() {
        return options.getNetworkId();
    }

}

