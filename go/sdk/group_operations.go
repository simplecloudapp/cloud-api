package simplecloud

import (
	"context"
	"net/http"
)

func (c *GroupsClient) Create(ctx context.Context, input ModelsCreateServerGroupRequest) (*Group, *http.Response, error) {
	result, response, err := c.api.ServerGroupsAPI.CreateServerGroup(ctx).
		XNetworkID(c.networkID).
		ModelsCreateServerGroupRequest(input).
		Execute()
	return mapWireResponse[Group](result, response, err)
}

func (c *GroupsClient) Update(ctx context.Context, id string, input ModelsPatchServerGroupRequest) (*Group, *http.Response, error) {
	result, response, err := c.api.ServerGroupsAPI.PatchServerGroup(ctx).
		XNetworkID(c.networkID).
		ServerGroupId(id).
		ModelsPatchServerGroupRequest(input).
		Execute()
	return mapWireResponse[Group](result, response, err)
}

func (c *GroupsClient) Delete(ctx context.Context, id string) (*ModelsDeleteServerGroupResponse, *http.Response, error) {
	return c.api.ServerGroupsAPI.DeleteServerGroup(ctx).
		XNetworkID(c.networkID).
		ServerGroupId(id).
		Execute()
}

func (c *GroupsClient) PatchProperties(ctx context.Context, id string, properties map[string]any) (map[string]any, *http.Response, error) {
	result, response, err := c.api.ServerGroupsAPI.PatchServerGroupProperties(ctx).
		XNetworkID(c.networkID).
		ServerGroupId(id).
		ModelsPatchPropertiesRequest(ModelsPatchPropertiesRequest{Properties: toWireProperties(properties)}).
		Execute()
	if err != nil || result == nil {
		return nil, response, err
	}
	return fromWireProperties(result.Properties), response, nil
}

func (c *GroupsClient) DeleteProperties(ctx context.Context, id string, keys ...string) (map[string]any, *http.Response, error) {
	result, response, err := c.api.ServerGroupsAPI.DeleteServerGroupProperties(ctx).
		XNetworkID(c.networkID).
		ServerGroupId(id).
		ModelsDeletePropertiesRequest(ModelsDeletePropertiesRequest{Keys: keys}).
		Execute()
	if err != nil || result == nil {
		return nil, response, err
	}
	return fromWireProperties(result.Properties), response, nil
}

func (c *GroupsClient) StartQueue(ctx context.Context) (*ModelsListServerGroupStartQueueResponse, *http.Response, error) {
	return c.api.ServerGroupsAPI.ListServerGroupStartQueue(ctx).
		XNetworkID(c.networkID).
		Execute()
}

func (c *GroupsClient) RequestStart(ctx context.Context, groupID string) (*ModelsQueueServerGroupStartResponse, *http.Response, error) {
	input := ModelsQueueServerGroupStartRequest{ServerGroupId: &groupID}
	return c.api.ServerGroupsAPI.QueueServerGroupStart(ctx).
		XNetworkID(c.networkID).
		ModelsQueueServerGroupStartRequest(input).
		Execute()
}

func (c *GroupsClient) ClearStartQueue(ctx context.Context, groupID string) (*ModelsClearServerGroupStartQueueResponse, *http.Response, error) {
	return c.api.ServerGroupsAPI.ClearServerGroupStartQueue(ctx).
		XNetworkID(c.networkID).
		ServerGroupId(groupID).
		Execute()
}
