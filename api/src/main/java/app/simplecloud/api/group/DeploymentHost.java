package app.simplecloud.api.group;

import org.jetbrains.annotations.Nullable;

public class DeploymentHost {
    @Nullable
    private String name;
    private int priority;

    public DeploymentHost() {
    }

    public static Builder builder() {
        return new Builder();
    }

    @Nullable
    public String getName() {
        return name;
    }

    public void setName(@Nullable String name) {
        this.name = name;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public static class Builder {
        private String name;
        private int priority;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder priority(int priority) {
            this.priority = priority;
            return this;
        }

        public DeploymentHost build() {
            DeploymentHost host = new DeploymentHost();
            host.setName(name);
            host.setPriority(priority);
            return host;
        }
    }
}
