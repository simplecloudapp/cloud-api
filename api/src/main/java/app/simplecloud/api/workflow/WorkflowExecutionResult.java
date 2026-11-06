package app.simplecloud.api.workflow;

import java.util.List;
import java.util.Map;

/**
 * Result of running a manual workflow on several servers.
 */
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
            Map<String, String> failedServers) {
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

    /**
     * @return the IDs of servers the workflow didn't run on, for example because they weren't eligible
     */
    public List<String> getSkippedServerIds() {
        return skippedServerIds;
    }

    /**
     * @return the failed servers, mapped from server ID to error message
     */
    public Map<String, String> getFailedServers() {
        return failedServers;
    }
}
