package app.simplecloud.api.workflow;

import java.util.List;
import java.util.Map;

public final class WorkflowExecutionResult {
    private final String workflowId;
    private final int totalServers;
    private final List<String> succeededServerIds;
    private final List<String> skippedServerIds;
    private final Map<String, String> failedServers;

    public WorkflowExecutionResult(
            String workflowId,
            int totalServers,
            List<String> succeededServerIds,
            List<String> skippedServerIds,
            Map<String, String> failedServers
    ) {
        this.workflowId = workflowId;
        this.totalServers = totalServers;
        this.succeededServerIds = List.copyOf(succeededServerIds);
        this.skippedServerIds = List.copyOf(skippedServerIds);
        this.failedServers = Map.copyOf(failedServers);
    }

    public String getWorkflowId() {
        return workflowId;
    }

    public int getTotalServers() {
        return totalServers;
    }

    public List<String> getSucceededServerIds() {
        return succeededServerIds;
    }

    public List<String> getSkippedServerIds() {
        return skippedServerIds;
    }

    public Map<String, String> getFailedServers() {
        return failedServers;
    }
}
