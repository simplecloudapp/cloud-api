package app.simplecloud.api.group;

import org.jetbrains.annotations.Nullable;

public class DeploymentConfig {
    private DeploymentHost[] hosts;
    @Nullable
    private DeploymentStrategy strategy;

    public DeploymentConfig() {
        this.hosts = new DeploymentHost[0];
    }

    public static Builder builder() {
        return new Builder();
    }

    public DeploymentHost[] getHosts() {
        return hosts;
    }

    public void setHosts(DeploymentHost[] hosts) {
        this.hosts = hosts;
    }

    @Nullable
    public DeploymentStrategy getStrategy() {
        return strategy;
    }

    public void setStrategy(@Nullable DeploymentStrategy strategy) {
        this.strategy = strategy;
    }

    public static class Builder {
        private DeploymentHost[] hosts;
        private boolean hostsSet;
        private DeploymentStrategy strategy;

        public Builder hosts(DeploymentHost... hosts) {
            this.hosts = hosts;
            this.hostsSet = true;
            return this;
        }

        public Builder strategy(DeploymentStrategy strategy) {
            this.strategy = strategy;
            return this;
        }

        public DeploymentConfig build() {
            DeploymentConfig config = new DeploymentConfig();
            if (hostsSet) {
                config.setHosts(hosts);
            }
            config.setStrategy(strategy);
            return config;
        }
    }
}

