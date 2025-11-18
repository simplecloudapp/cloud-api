package app.simplecloud.api.group;

import org.jetbrains.annotations.Nullable;

public class DeploymentHost {
    @Nullable
    private String name;
    private int priority;

    public DeploymentHost() {
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
}
