package simplecloud

import (
	"context"
	"net/http"
)

func (c *GroupsClient) StartRollingRestart(ctx context.Context, id string) (*ModelsRollingRestartResponse, *http.Response, error) {
	input := ModelsRollingRestartRequest{ServerGroupId: &id}
	return c.api.ServerGroupsAPI.StartRollingRestart(ctx).
		XNetworkID(c.networkID).
		ModelsRollingRestartRequest(input).
		Execute()
}

func (c *GroupsClient) RollingRestartStatus(ctx context.Context, id string) (*ModelsRollingRestartStatusResponse, *http.Response, error) {
	return c.api.ServerGroupsAPI.GetRollingRestartStatus(ctx).
		XNetworkID(c.networkID).
		GroupId(id).
		Execute()
}

func (c *GroupsClient) CancelRollingRestart(ctx context.Context, id string) (*ModelsCancelRollingRestartResponse, *http.Response, error) {
	return c.api.ServerGroupsAPI.CancelRollingRestart(ctx).
		XNetworkID(c.networkID).
		GroupId(id).
		Execute()
}

func (c *GroupsClient) StopAllServers(ctx context.Context, id string) (int32, *http.Response, error) {
	result, response, err := c.api.ServerGroupsAPI.StopAllServers(ctx).
		XNetworkID(c.networkID).
		ServerGroupId(id).
		Execute()
	if err != nil || result == nil {
		return 0, response, err
	}
	return result.GetServersStopped(), response, nil
}
