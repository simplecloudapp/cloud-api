package app.simplecloud.api.group;

import org.jetbrains.annotations.Nullable;

public class ScaleDownConfig {
    @Nullable
    private String idleTime;
    private boolean ignorePlayers;
    private boolean ignorePlayersSet;

    public ScaleDownConfig() {
    }

    public static Builder builder() {
        return new Builder();
    }

    @Nullable
    public String getIdleTime() {
        return idleTime;
    }

    public void setIdleTime(@Nullable String idleTime) {
        this.idleTime = idleTime;
    }

    public boolean isIgnorePlayers() {
        return ignorePlayers;
    }

    public void setIgnorePlayers(boolean ignorePlayers) {
        this.ignorePlayers = ignorePlayers;
        this.ignorePlayersSet = true;
    }

    public boolean hasIgnorePlayers() {
        return ignorePlayersSet;
    }

    public static class Builder {
        private String idleTime;
        private boolean ignorePlayers;
        private boolean ignorePlayersSet;

        public Builder idleTime(String idleTime) {
            this.idleTime = idleTime;
            return this;
        }

        public Builder ignorePlayers(boolean ignorePlayers) {
            this.ignorePlayers = ignorePlayers;
            this.ignorePlayersSet = true;
            return this;
        }

        public ScaleDownConfig build() {
            ScaleDownConfig config = new ScaleDownConfig();
            config.setIdleTime(idleTime);
            if (ignorePlayersSet) {
                config.setIgnorePlayers(ignorePlayers);
            }
            return config;
        }
    }
}
