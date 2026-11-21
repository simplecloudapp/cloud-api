package app.simplecloud.api.internal.workflow;

import app.simplecloud.api.web.models.ModelsExecuteManualWorkflowFailure;
import app.simplecloud.api.workflow.WorkflowExecutionResult;
import app.simplecloud.api.workflow.WorkflowFailure;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

public final class WorkflowResults {

    private WorkflowResults() {
    }

    public static WorkflowExecutionResult of(String requestedWorkflowId,
                                             @Nullable String workflowId,
                                             @Nullable Integer totalServers,
                                             @Nullable List<String> succeededServerIds,
                                             @Nullable List<String> skippedServerIds,
                                             @Nullable List<ModelsExecuteManualWorkflowFailure> failedServers) {
        List<WorkflowFailure> failures = Objects.requireNonNullElse(failedServers, List.<ModelsExecuteManualWorkflowFailure>of())
                .stream()
                .map(failure -> new WorkflowFailure(Objects.requireNonNullElse(failure.getServerId(), ""), failure.getErrorMessage()))
                .toList();
        return new WorkflowExecutionResult(
                Objects.requireNonNullElse(workflowId, requestedWorkflowId),
                totalServers != null ? totalServers : 0,
                Objects.requireNonNullElse(succeededServerIds, List.of()),
                Objects.requireNonNullElse(skippedServerIds, List.of()),
                failures
        );
    }
}
