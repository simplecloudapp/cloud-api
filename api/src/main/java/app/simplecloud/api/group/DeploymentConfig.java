package app.simplecloud.api.group;

import org.jetbrains.annotations.Nullable;

public class DeploymentConfig {
    private DeploymentHost[] hosts;
    @Nullable
    private DeploymentStrategy strategy;

    public DeploymentConfig() {
        this.hosts = new DeploymentHost[0];
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
}


