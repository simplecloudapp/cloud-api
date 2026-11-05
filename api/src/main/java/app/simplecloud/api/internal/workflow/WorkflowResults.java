package app.simplecloud.api.internal.workflow;

import app.simplecloud.api.web.models.ModelsExecuteManualWorkflowFailure;
import app.simplecloud.api.web.models.ModelsExecuteManualWorkflowRequest;
import app.simplecloud.api.web.models.ModelsExecutePersistentServerWorkflowResponse;
import app.simplecloud.api.web.models.ModelsExecuteServerGroupWorkflowResponse;
import app.simplecloud.api.web.models.ModelsExecuteServerWorkflowResponse;
import app.simplecloud.api.workflow.WorkflowExecutionResult;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class WorkflowResults {

    private WorkflowResults() {
    }

    public static ModelsExecuteManualWorkflowRequest request(String workflowId) {
        if (workflowId == null || workflowId.isBlank()) {
            throw new IllegalArgumentException("workflowId must not be blank");
        }
        ModelsExecuteManualWorkflowRequest request = new ModelsExecuteManualWorkflowRequest();
        request.setWorkflowId(workflowId);
        return request;
    }

    public static WorkflowExecutionResult fromServer(String serverId, String workflowId, ModelsExecuteServerWorkflowResponse response) {
        if (Boolean.TRUE.equals(response.getSuccess())) {
            return new WorkflowExecutionResult(workflowId, 1, List.of(serverId), List.of(), Map.of());
        }
        if (response.getErrorMessage() != null) {
            return new WorkflowExecutionResult(workflowId, 1, List.of(), List.of(), Map.of(serverId, response.getErrorMessage()));
        }
        return new WorkflowExecutionResult(workflowId, 1, List.of(), List.of(serverId), Map.of());
    }

    public static WorkflowExecutionResult fromServerGroup(String workflowId, ModelsExecuteServerGroupWorkflowResponse response) {
        return create(
                workflowId,
                response.getTotalServers(),
                response.getSucceededServerIds(),
                response.getSkippedServerIds(),
                response.getFailedServers()
        );
    }

    public static WorkflowExecutionResult fromPersistentServer(String workflowId, ModelsExecutePersistentServerWorkflowResponse response) {
        return create(
                workflowId,
                response.getTotalServers(),
                response.getSucceededServerIds(),
                response.getSkippedServerIds(),
                response.getFailedServers()
        );
    }

    private static WorkflowExecutionResult create(
            String workflowId,
            Integer totalServers,
            List<String> succeededServerIds,
            List<String> skippedServerIds,
            List<ModelsExecuteManualWorkflowFailure> failures
    ) {
        Map<String, String> failedServers = new LinkedHashMap<>();
        if (failures != null) {
            for (ModelsExecuteManualWorkflowFailure failure : failures) {
                if (failure.getServerId() != null) {
                    failedServers.put(failure.getServerId(), failure.getErrorMessage() != null ? failure.getErrorMessage() : "");
                }
            }
        }
        return new WorkflowExecutionResult(
                workflowId,
                totalServers != null ? totalServers : 0,
                succeededServerIds != null ? succeededServerIds : List.of(),
                skippedServerIds != null ? skippedServerIds : List.of(),
                failedServers
        );
    }
}
