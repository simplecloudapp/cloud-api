package simplecloud

import (
	"context"
	"net/http"
)

func (c *GroupsClient) Replace(ctx context.Context, id string, input ModelsUpdateServerGroupRequest) (*Group, *http.Response, error) {
	result, response, err := c.api.ServerGroupsAPI.UpdateServerGroup(ctx).
		XNetworkID(c.networkID).
		ServerGroupId(id).
		ModelsUpdateServerGroupRequest(input).
		Execute()
	return mapWireResponse[Group](result, response, err)
}

func (c *PersistentServersClient) Replace(ctx context.Context, id string, input ModelsUpdatePersistentServerRequest) (*PersistentServer, *http.Response, error) {
	result, response, err := c.api.PersistentServersAPI.UpdatePersistentServer(ctx).
		XNetworkID(c.networkID).
		PersistentServerId(id).
		ModelsUpdatePersistentServerRequest(input).
		Execute()
	return mapWireResponse[PersistentServer](result, response, err)
}
