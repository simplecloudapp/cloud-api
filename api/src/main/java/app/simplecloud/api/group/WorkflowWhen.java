package app.simplecloud.api.group;

import org.jetbrains.annotations.Nullable;

import java.util.List;

public class WorkflowWhen {
    @Nullable
    private List<String> start;
    @Nullable
    private List<String> stop;

    public WorkflowWhen() {
    }

    public static Builder builder() {
        return new Builder();
    }

    @Nullable
    public List<String> getStart() {
        return start;
    }

    public void setStart(@Nullable List<String> start) {
        this.start = start;
    }

    @Nullable
    public List<String> getStop() {
        return stop;
    }

    public void setStop(@Nullable List<String> stop) {
        this.stop = stop;
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
            WorkflowWhen when = new WorkflowWhen();
            when.setStart(start);
            when.setStop(stop);
            return when;
        }
    }
}
