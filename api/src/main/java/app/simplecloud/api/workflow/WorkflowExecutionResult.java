package app.simplecloud.api.workflow;

import java.util.List;

/**
 * Result of running a manual workflow on every server of a group or persistent server.
 *
 * @param workflowId         the executed workflow
 * @param totalServers       the number of servers the workflow was considered for
 * @param succeededServerIds the servers the workflow succeeded on
 * @param skippedServerIds   the servers that were skipped
 * @param failedServers      the servers the workflow failed on
 */
public record WorkflowExecutionResult(
        String workflowId,
        int totalServers,
        List<String> succeededServerIds,
        List<String> skippedServerIds,
        List<WorkflowFailure> failedServers
) {
    public WorkflowExecutionResult {
        succeededServerIds = List.copyOf(succeededServerIds);
        skippedServerIds = List.copyOf(skippedServerIds);
        failedServers = List.copyOf(failedServers);
    }
}
