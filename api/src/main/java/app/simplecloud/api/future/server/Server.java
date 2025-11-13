package app.simplecloud.api.future.server;

import java.util.Map;

public interface Server {
    String getServerId();
    String getPersistentServerId();
    int getNumericalId();
    String getServerGroupId();
    String getServerhostId();
    String getNetworkId();
    String getIp();
    int getPort();

    int getMinMemory();
    int getMaxMemory();

    double getCpuUsage();
    double getMemoryUsage();
    int getPlayerCount();
    int getMaxPlayers();

    String getState();
    String getCreatedAt();
    String getUpdatedAt();
    String getLastActivity();

    Map<String, Object> getProperties();
}
