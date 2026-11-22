package app.simplecloud.api.group;

import java.util.List;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

public record WorkflowWhen(
        @Nullable List<String> start, @Nullable List<String> stop) {

    /**
     * @deprecated use {@link #start()}; will be removed in 1.0.0
     */
    @Deprecated(forRemoval = true)
    @ApiStatus.ScheduledForRemoval(inVersion = "1.0.0")
    @Nullable public List<String> getStart() {
        return start;
    }

    /**
     * @deprecated use {@link #stop()}; will be removed in 1.0.0
     */
    @Deprecated(forRemoval = true)
    @ApiStatus.ScheduledForRemoval(inVersion = "1.0.0")
    @Nullable public List<String> getStop() {
        return stop;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private List<String> start;
        private List<String> stop;

        public Builder start(List<String> start) {
            this.start = start;
            return this;
        }

        public Builder stop(List<String> stop) {
            this.stop = stop;
            return this;
        }

        public WorkflowWhen build() {
            return new WorkflowWhen(start, stop);
        }
    }
}
