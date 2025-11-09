package app.simplecloud.api.future;

import app.simplecloud.api.CloudApiOptions;
import app.simplecloud.api.future.event.EventApi;
import app.simplecloud.api.future.group.GroupApi;
import app.simplecloud.api.future.server.ServerApi;

public interface CloudApi {

    static CloudApi create() {
        return create(CloudApiOptions.DEFAULT);
    }

    static CloudApi create(CloudApiOptions options) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    GroupApi group();

    ServerApi server();

    EventApi event();

}
