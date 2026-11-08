package app.simplecloud.api.group;

import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * A host listed in a {@link DeploymentConfig}, referenced by its name.
 */
public class DeploymentHost {
    @Nullable
    private String name;

    private int priority;

    /**
     * @deprecated use {@link #DeploymentHost(String, int)}
     */
    @Deprecated
    public DeploymentHost() {}

    /**
     * @param name the name of the serverhost
     * @param priority the priority of this host
     */
    public DeploymentHost(@Nullable String name, int priority) {
        this.name = name;
        this.priority = priority;
    }

    /**
     * @deprecated use {@link #DeploymentHost(String, int)}
     */
    @Deprecated
    public static Builder builder() {
        return new Builder();
    }

    /**
     * @return the name of the serverhost
     */
    @Nullable
    public String name() {
        return name;
    }

    /**
     * @return the priority of this host
     */
    public int priority() {
        return priority;
    }

    /** @deprecated use {@link #name()} */
    @Deprecated
    @Nullable
    public String getName() {
        return name;
    }

    /** @deprecated create a new host with {@link #DeploymentHost(String, int)} */
    @Deprecated
    public void setName(@Nullable String name) {
        this.name = name;
    }

    /** @deprecated use {@link #priority()} */
    @Deprecated
    public int getPriority() {
        return priority;
    }

    /** @deprecated create a new host with {@link #DeploymentHost(String, int)} */
    @Deprecated
    public void setPriority(int priority) {
        this.priority = priority;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof DeploymentHost other && priority == other.priority && Objects.equals(name, other.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, priority);
    }

    @Override
    public String toString() {
        return "DeploymentHost[name=" + name + ", priority=" + priority + "]";
    }

    /**
     * @deprecated use {@link #DeploymentHost(String, int)}
     */
    @Deprecated
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
