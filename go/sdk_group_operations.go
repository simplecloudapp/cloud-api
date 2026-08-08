package simplecloud

import (
	"context"
	"net/http"
)

func (c *GroupsClient) Create(ctx context.Context, input ModelsCreateServerGroupRequest) (*Group, *http.Response, error) {
	result, response, err := c.api.ServerGroupsAPI.V0ServerGroupsPost(ctx).
		XNetworkID(c.networkID).
		XNetworkCredential(c.credential).
		ModelsCreateServerGroupRequest(input).
		Execute()
	return mapWireResponse[Group](result, response, err)
}

func (c *GroupsClient) Update(ctx context.Context, id string, input ModelsPatchServerGroupRequest) (*Group, *http.Response, error) {
	result, response, err := c.api.ServerGroupsAPI.V0ServerGroupsPatch(ctx).
		XNetworkID(c.networkID).
		XNetworkCredential(c.credential).
		ServerGroupId(id).
		ModelsPatchServerGroupRequest(input).
		Execute()
	return mapWireResponse[Group](result, response, err)
}

func (c *GroupsClient) Delete(ctx context.Context, id string) (*ModelsDeleteServerGroupResponse, *http.Response, error) {
	return c.api.ServerGroupsAPI.V0ServerGroupsDelete(ctx).
		XNetworkID(c.networkID).
		XNetworkCredential(c.credential).
		ServerGroupId(id).
		Execute()
}

func (c *GroupsClient) PatchProperties(ctx context.Context, id string, properties map[string]any) (map[string]any, *http.Response, error) {
	result, response, err := c.api.ServerGroupsAPI.V0ServerGroupsPropertiesPatch(ctx).
		XNetworkID(c.networkID).
		XNetworkCredential(c.credential).
		ServerGroupId(id).
		ModelsPatchPropertiesRequest(ModelsPatchPropertiesRequest{Properties: properties}).
		Execute()
	if err != nil || result == nil {
		return nil, response, err
	}
	return result.Properties, response, nil
}

func (c *GroupsClient) DeleteProperties(ctx context.Context, id string, keys ...string) (map[string]any, *http.Response, error) {
	result, response, err := c.api.ServerGroupsAPI.V0ServerGroupsPropertiesDelete(ctx).
		XNetworkID(c.networkID).
		XNetworkCredential(c.credential).
		ServerGroupId(id).
		ModelsDeletePropertiesRequest(ModelsDeletePropertiesRequest{Keys: keys}).
		Execute()
	if err != nil || result == nil {
		return nil, response, err
	}
	return result.Properties, response, nil
}

func (c *GroupsClient) StartQueue(ctx context.Context) (*ModelsListServerGroupStartQueueResponse, *http.Response, error) {
	return c.api.ServerGroupsAPI.V0ServerGroupsStartQueueGet(ctx).
		XNetworkID(c.networkID).
		XNetworkCredential(c.credential).
		Execute()
}

func (c *GroupsClient) RequestStart(ctx context.Context, groupID string) (*ModelsQueueServerGroupStartResponse, *http.Response, error) {
	input := ModelsQueueServerGroupStartRequest{ServerGroupId: &groupID}
	return c.api.ServerGroupsAPI.V0ServerGroupsStartQueuePost(ctx).
		XNetworkID(c.networkID).
		XNetworkCredential(c.credential).
		ModelsQueueServerGroupStartRequest(input).
		Execute()
}

func (c *GroupsClient) ClearStartQueue(ctx context.Context, groupID string) (*ModelsClearServerGroupStartQueueResponse, *http.Response, error) {
	return c.api.ServerGroupsAPI.V0ServerGroupsStartQueueDelete(ctx).
		XNetworkID(c.networkID).
		XNetworkCredential(c.credential).
		ServerGroupId(groupID).
		Execute()
}
