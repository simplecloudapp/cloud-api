package app.simplecloud.api.workflow;

import org.jetbrains.annotations.Nullable;

/**
 * Result of running a manual workflow on a single server.
 *
 * @param serverId     the server the workflow ran on
 * @param workflowId   the executed workflow
 * @param success      whether the workflow succeeded
 * @param errorMessage the failure reason, or null if the workflow succeeded or no reason is known
 */
public record ServerWorkflowResult(
        String serverId,
        String workflowId,
        boolean success,
        @Nullable String errorMessage) {}
