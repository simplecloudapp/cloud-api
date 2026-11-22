package app.simplecloud.api.group;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

public record DeploymentHost(@Nullable String name, int priority) {

    /**
     * @deprecated use {@link #name()}; will be removed in 1.0.0
     */
    @Deprecated(forRemoval = true)
    @ApiStatus.ScheduledForRemoval(inVersion = "1.0.0")
    @Nullable public String getName() {
        return name;
    }

    /**
     * @deprecated use {@link #priority()}; will be removed in 1.0.0
     */
    @Deprecated(forRemoval = true)
    @ApiStatus.ScheduledForRemoval(inVersion = "1.0.0")
    public int getPriority() {
        return priority;
    }

    public static Builder builder() {
        return new Builder();
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
            return new DeploymentHost(name, priority);
        }
    }
}
