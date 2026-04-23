package app.simplecloud.api.server;

import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Request object for updating an existing server instance.
 */
public class UpdateServerRequest {
    @Nullable
    private Integer playerCount;
    @Nullable
    private ServerState state;
    @Nullable
    private Map<String, Object> properties;
    @Nullable
    private Integer minMemory;
    @Nullable
    private Integer maxMemory;
    @Nullable
    private Integer maxPlayers;

    public UpdateServerRequest() {
    }

    public static Builder builder() {
        return new Builder();
    }

    @Nullable
    public Integer getPlayerCount() {
        return playerCount;
    }

    public void setPlayerCount(@Nullable Integer playerCount) {
        this.playerCount = playerCount;
    }

    @Nullable
    public ServerState getState() {
        return state;
    }

    public void setState(@Nullable ServerState state) {
        this.state = state;
    }

    @Nullable
    public Map<String, Object> getProperties() {
        return properties;
    }

    public void setProperties(@Nullable Map<String, Object> properties) {
        this.properties = properties;
    }

    @Nullable
    public Integer getMinMemory() {
        return minMemory;
    }

    public void setMinMemory(@Nullable Integer minMemory) {
        this.minMemory = minMemory;
    }

    @Nullable
    public Integer getMaxMemory() {
        return maxMemory;
    }

    public void setMaxMemory(@Nullable Integer maxMemory) {
        this.maxMemory = maxMemory;
    }

    @Nullable
    public Integer getMaxPlayers() {
        return maxPlayers;
    }

    public void setMaxPlayers(@Nullable Integer maxPlayers) {
        this.maxPlayers = maxPlayers;
    }

    public static class Builder {
        private Integer playerCount;
        private ServerState state;
        private Map<String, Object> properties;
        private Integer minMemory;
        private Integer maxMemory;
        private Integer maxPlayers;

        public Builder playerCount(Integer playerCount) {
            this.playerCount = playerCount;
            return this;
        }

        public Builder state(ServerState state) {
            this.state = state;
            return this;
        }

        public Builder properties(Map<String, Object> properties) {
            this.properties = properties;
            return this;
        }

        public Builder minMemory(Integer minMemory) {
            this.minMemory = minMemory;
            return this;
        }

        public Builder maxMemory(Integer maxMemory) {
            this.maxMemory = maxMemory;
            return this;
        }

        public Builder maxPlayers(Integer maxPlayers) {
            this.maxPlayers = maxPlayers;
            return this;
        }

        public UpdateServerRequest build() {
            UpdateServerRequest request = new UpdateServerRequest();
            request.setPlayerCount(playerCount);
            request.setState(state);
            request.setProperties(properties);
            request.setMinMemory(minMemory);
            request.setMaxMemory(maxMemory);
            request.setMaxPlayers(maxPlayers);
            return request;
        }
    }
}

