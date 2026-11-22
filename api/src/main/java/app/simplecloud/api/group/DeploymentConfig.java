package app.simplecloud.api.group;

import java.util.List;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

public record DeploymentConfig(
        @Nullable List<DeploymentHost> hosts, @Nullable DeploymentStrategy strategy) {

    public DeploymentConfig {
        hosts = hosts != null ? List.copyOf(hosts) : null;
    }

    public boolean hasHosts() {
        return hosts != null;
    }

    /**
     * @deprecated use {@link #hosts()}; will be removed in 1.0.0
     */
    @Deprecated(forRemoval = true)
    @ApiStatus.ScheduledForRemoval(inVersion = "1.0.0")
    public DeploymentHost[] getHosts() {
        return hosts != null ? hosts.toArray(DeploymentHost[]::new) : new DeploymentHost[0];
    }

    /**
     * @deprecated use {@link #strategy()}; will be removed in 1.0.0
     */
    @Deprecated(forRemoval = true)
    @ApiStatus.ScheduledForRemoval(inVersion = "1.0.0")
    @Nullable public DeploymentStrategy getStrategy() {
        return strategy;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private List<DeploymentHost> hosts;
        private DeploymentStrategy strategy;

        public Builder hosts(DeploymentHost... hosts) {
            this.hosts = hosts != null ? List.of(hosts) : List.of();
            return this;
        }

        public Builder hosts(List<DeploymentHost> hosts) {
            this.hosts = hosts != null ? hosts : List.of();
            return this;
        }

        public Builder strategy(DeploymentStrategy strategy) {
            this.strategy = strategy;
            return this;
        }

        public DeploymentConfig build() {
            return new DeploymentConfig(hosts, strategy);
        }
    }
}
