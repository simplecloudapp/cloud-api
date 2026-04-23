package app.simplecloud.api.group;

import org.jetbrains.annotations.Nullable;

import java.util.List;

public class WorkflowsConfig {
    @Nullable
    private List<String> manual;
    @Nullable
    private WorkflowWhen when;

    public WorkflowsConfig() {
    }

    public static Builder builder() {
        return new Builder();
    }

    @Nullable
    public List<String> getManual() {
        return manual;
    }

    public void setManual(@Nullable List<String> manual) {
        this.manual = manual;
    }

    @Nullable
    public WorkflowWhen getWhen() {
        return when;
    }

    public void setWhen(@Nullable WorkflowWhen when) {
        this.when = when;
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
            WorkflowsConfig config = new WorkflowsConfig();
            config.setManual(manual);
            config.setWhen(when);
            return config;
        }
    }
}
