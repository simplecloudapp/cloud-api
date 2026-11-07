package app.simplecloud.api.workflow;

import java.util.List;
import java.util.Map;

/**
 * Result of running a manual workflow on several servers.
 *
 * @param workflowId         the ID of the executed workflow
 * @param totalServers       the number of servers the workflow was requested for
 * @param succeededServerIds the IDs of servers the workflow ran on successfully
 * @param skippedServerIds   the IDs of servers the workflow didn't run on, for example because they weren't eligible
 * @param failedServers      the failed servers, mapped from server ID to error message
 */
public record WorkflowExecutionResult(
        String workflowId,
        int totalServers,
        List<String> succeededServerIds,
        List<String> skippedServerIds,
        Map<String, String> failedServers) {
    public WorkflowExecutionResult {
        succeededServerIds = List.copyOf(succeededServerIds);
        skippedServerIds = List.copyOf(skippedServerIds);
        failedServers = Map.copyOf(failedServers);
    }
}
