package app.simplecloud.api.server;

import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Request object for starting a new server instance.
 */
public class StartServerRequest {
    private String serverGroupId;
    private String serverGroupName;
    @Nullable
    private String serverhostId;
    private Map<String, Object> properties;

    public StartServerRequest(String serverGroupId, String serverGroupName) {
        this.serverGroupId = serverGroupId;
        this.serverGroupName = serverGroupName;
        this.properties = Map.of();
    }

    public String getServerGroupId() {
        return serverGroupId;
    }

    public void setServerGroupId(String serverGroupId) {
        this.serverGroupId = serverGroupId;
    }

    public String getServerGroupName() {
        return serverGroupName;
    }

    public void setServerGroupName(String serverGroupName) {
        this.serverGroupName = serverGroupName;
    }

    @Nullable
    public String getServerhostId() {
        return serverhostId;
    }

    public void setServerhostId(@Nullable String serverhostId) {
        this.serverhostId = serverhostId;
    }

    public Map<String, Object> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, Object> properties) {
        this.properties = properties;
    }
}

