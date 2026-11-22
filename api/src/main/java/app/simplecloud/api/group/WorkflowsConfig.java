package app.simplecloud.api.group;

import java.util.List;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

public record WorkflowsConfig(
        @Nullable List<String> manual, @Nullable WorkflowWhen when) {

    /**
     * @deprecated use {@link #manual()}; will be removed in 1.0.0
     */
    @Deprecated(forRemoval = true)
    @ApiStatus.ScheduledForRemoval(inVersion = "1.0.0")
    @Nullable public List<String> getManual() {
        return manual;
    }

    /**
     * @deprecated use {@link #when()}; will be removed in 1.0.0
     */
    @Deprecated(forRemoval = true)
    @ApiStatus.ScheduledForRemoval(inVersion = "1.0.0")
    @Nullable public WorkflowWhen getWhen() {
        return when;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private List<String> manual;
        private WorkflowWhen when;

        public Builder manual(List<String> manual) {
            this.manual = manual;
            return this;
        }

        public Builder when(WorkflowWhen when) {
            this.when = when;
            return this;
        }

        public WorkflowsConfig build() {
            return new WorkflowsConfig(manual, when);
        }
    }
}
