package simplecloud

import (
	"context"
	"net/http"
)

func (c *GroupsClient) ExecuteWorkflow(ctx context.Context, id, workflowID string) (*ModelsExecuteServerGroupWorkflowResponse, *http.Response, error) {
	input := ModelsExecuteManualWorkflowRequest{WorkflowId: &workflowID}
	return c.api.ServerGroupsAPI.ExecuteServerGroupWorkflow(ctx).
		XNetworkID(c.networkID).
		ServerGroupId(id).
		ModelsExecuteManualWorkflowRequest(input).
		Execute()
}

func (c *ServersClient) ExecuteWorkflow(ctx context.Context, id, workflowID string) (*ModelsExecuteServerWorkflowResponse, *http.Response, error) {
	input := ModelsExecuteManualWorkflowRequest{WorkflowId: &workflowID}
	return c.api.ServersAPI.ExecuteServerWorkflow(ctx).
		XNetworkID(c.networkID).
		ServerId(id).
		ModelsExecuteManualWorkflowRequest(input).
		Execute()
}

func (c *PersistentServersClient) ExecuteWorkflow(ctx context.Context, id, workflowID string) (*ModelsExecutePersistentServerWorkflowResponse, *http.Response, error) {
	input := ModelsExecuteManualWorkflowRequest{WorkflowId: &workflowID}
	return c.api.PersistentServersAPI.ExecutePersistentServerWorkflow(ctx).
		XNetworkID(c.networkID).
		PersistentServerId(id).
		ModelsExecuteManualWorkflowRequest(input).
		Execute()
}
