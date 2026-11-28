package simplecloud

import (
	"context"
	"net/http"
)

func (c *GroupsClient) ConvertToPersistentServer(ctx context.Context, id, serverhostID string) (*PersistentServer, *http.Response, error) {
	input := ModelsConvertGroupToPersistentRequest{ServerhostId: serverhostID}
	result, response, err := c.api.ServerGroupsAPI.ConvertGroupToPersistent(ctx).
		XNetworkID(c.networkID).
		ServerGroupId(id).
		ModelsConvertGroupToPersistentRequest(input).
		Execute()
	return mapWireResponse[PersistentServer](result, response, err)
}

func (c *PersistentServersClient) ConvertToGroup(ctx context.Context, id string, hosts []ModelsDeploymentHost, scaling ModelsScalingConfig) (*Group, *http.Response, error) {
	input := ModelsConvertPersistentToGroupRequest{
		Deployment: ModelsConversionDeployment{Hosts: hosts},
		Scaling:    scaling,
	}
	result, response, err := c.api.PersistentServersAPI.ConvertPersistentToGroup(ctx).
		XNetworkID(c.networkID).
		PersistentServerId(id).
		ModelsConvertPersistentToGroupRequest(input).
		Execute()
	return mapWireResponse[Group](result, response, err)
}
