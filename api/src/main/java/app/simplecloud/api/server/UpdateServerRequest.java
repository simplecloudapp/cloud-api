package app.simplecloud.api.server;

import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Request object for updating an existing server instance.
 */
public class UpdateServerRequest {
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

    @Nullable
    private Integer playerCount;

    public UpdateServerRequest() {}

    /**
     * @return the player count set with {@link #setPlayerCount(Integer)}
     * @deprecated the controller tracks the player count itself and ignores this value
     */
    @Deprecated
    @Nullable
    public Integer getPlayerCount() {
        return playerCount;
    }

    /**
     * @param playerCount the player count
     * @deprecated the controller tracks the player count itself and ignores this value
     */
    @Deprecated
    public void setPlayerCount(@Nullable Integer playerCount) {
        this.playerCount = playerCount;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Nullable
    public ServerState getState() {
        return state;
    }

    /**
     * Sets the state the controller stores for this server.
     *
     * <p>This only overwrites the stored state and publishes the matching events. It does not start or stop the
     * server process; use {@link ServerApi#stopServer(String)} to stop a server. The controller plans to stop
     * accepting {@code state} here.
     *
     * @param state the state to store, or null to keep it
     */
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
        private ServerState state;
        private Map<String, Object> properties;
        private Integer minMemory;
        private Integer maxMemory;
        private Integer maxPlayers;
        private Integer playerCount;

        /**
         * @param playerCount the player count
         * @return this builder
         * @deprecated the controller tracks the player count itself and ignores this value
         */
        @Deprecated
        public Builder playerCount(Integer playerCount) {
            this.playerCount = playerCount;
            return this;
        }

        /**
         * Sets the state the controller stores for this server. See {@link UpdateServerRequest#setState}.
         *
         * @param state the state to store
         * @return this builder
         */
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
            request.setState(state);
            request.setProperties(properties);
            request.setMinMemory(minMemory);
            request.setMaxMemory(maxMemory);
            request.setMaxPlayers(maxPlayers);
            request.setPlayerCount(playerCount);
            return request;
        }
    }
}
