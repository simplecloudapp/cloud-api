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
}


