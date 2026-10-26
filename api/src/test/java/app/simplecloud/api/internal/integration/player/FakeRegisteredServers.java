package app.simplecloud.api.internal.integration.player;

import java.util.Map;
import java.util.Optional;

final class FakeRegisteredServers implements RegisteredServers<String> {

    private final Map<String, String> serversByName;
    private final Map<String, String> serversById;

    FakeRegisteredServers(Map<String, String> serversByName, Map<String, String> serversById) {
        this.serversByName = serversByName;
        this.serversById = serversById;
    }

    @Override
    public Optional<String> findByName(String name) {
        String server = serversByName.get(name);
        return Optional.ofNullable(server);
    }

    // Answers IDs from a map, so ConnectTarget tests don't depend on the process-wide ProxyServerNames.
    @Override
    public Optional<String> findById(String serverId) {
        String server = serversById.get(serverId);
        return Optional.ofNullable(server);
    }
}
