package app.simplecloud.api.group;

import org.jetbrains.annotations.Nullable;

public class ScaleDownConfig {
    @Nullable
    private String idleTime;
    private boolean ignorePlayers;
    private boolean ignorePlayersSet;

    public ScaleDownConfig() {
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
}
