package app.simplecloud.api.model;

public interface DeploymentConfig {
    DeploymentHost[] getHosts();
    String getStrategy();
}