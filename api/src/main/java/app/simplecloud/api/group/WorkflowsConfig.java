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
}