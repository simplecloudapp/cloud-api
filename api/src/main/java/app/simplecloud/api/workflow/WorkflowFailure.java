package app.simplecloud.api.workflow;

import org.jetbrains.annotations.Nullable;

/**
 * A server a manual workflow failed on.
 *
 * @param serverId     the server ID
 * @param errorMessage the failure reason, or null if none is known
 */
public record WorkflowFailure(String serverId, @Nullable String errorMessage) {}
