package simplecloud

import (
	"context"
	"net/http"
)

func (c *GroupsClient) SetProperties(ctx context.Context, id string, properties map[string]any) (map[string]any, *http.Response, error) {
	input := ModelsUpdatePropertiesRequest{Properties: toWireProperties(properties)}
	result, response, err := c.api.ServerGroupsAPI.UpdateServerGroupProperties(ctx).
		XNetworkID(c.networkID).
		ServerGroupId(id).
		ModelsUpdatePropertiesRequest(input).
		Execute()
	if err != nil || result == nil {
		return nil, response, err
	}
	return fromWireProperties(result.Properties), response, nil
}

func (c *ServersClient) SetProperties(ctx context.Context, id string, properties map[string]any) (map[string]any, *http.Response, error) {
	input := ModelsUpdatePropertiesRequest{Properties: toWireProperties(properties)}
	result, response, err := c.api.ServersAPI.UpdateServerProperties(ctx).
		XNetworkID(c.networkID).
		ServerId(id).
		ModelsUpdatePropertiesRequest(input).
		Execute()
	if err != nil || result == nil {
		return nil, response, err
	}
	return fromWireProperties(result.Properties), response, nil
}

func (c *PersistentServersClient) SetProperties(ctx context.Context, id string, properties map[string]any) (map[string]any, *http.Response, error) {
	input := ModelsUpdatePropertiesRequest{Properties: toWireProperties(properties)}
	result, response, err := c.api.PersistentServersAPI.UpdatePersistentServerProperties(ctx).
		XNetworkID(c.networkID).
		PersistentServerId(id).
		ModelsUpdatePropertiesRequest(input).
		Execute()
	if err != nil || result == nil {
		return nil, response, err
	}
	return fromWireProperties(result.Properties), response, nil
}
