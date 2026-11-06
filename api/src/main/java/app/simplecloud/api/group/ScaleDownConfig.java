package app.simplecloud.api.group;

import org.jetbrains.annotations.Nullable;

/**
 * When idle servers of a group are stopped.
 */
public class ScaleDownConfig {
    @Nullable
    private String idleTime;

    private boolean ignorePlayers;
    private boolean ignorePlayersSet;

    public ScaleDownConfig() {}

    public static Builder builder() {
        return new Builder();
    }

    /**
     * @return how long a server has to be idle before it is stopped, as a duration like {@code "3m"},
     *         or null if not set
     */
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

    /**
     * @return whether {@code ignorePlayers} was set; if not, the create default {@code true} is used
     */
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
