package simplecloud

import (
	"context"
	"net/http"
)

func (c *ServersClient) Stop(ctx context.Context, id string) (*ModelsStopServerResponse, *http.Response, error) {
	return c.api.ServersAPI.StopServer(ctx).
		XNetworkID(c.networkID).
		ServerId(id).
		Execute()
}

func (c *ServersClient) Update(ctx context.Context, id string, input ModelsPatchServerRequest) (*Server, *http.Response, error) {
	result, response, err := c.api.ServersAPI.PatchServer(ctx).
		XNetworkID(c.networkID).
		ServerId(id).
		ModelsPatchServerRequest(input).
		Execute()
	if err != nil || result == nil {
		return nil, response, err
	}
	return result.Server, response, nil
}

func (c *ServersClient) PatchProperties(ctx context.Context, id string, properties map[string]any) (map[string]any, *http.Response, error) {
	input := ModelsPatchPropertiesRequest{Properties: toWireProperties(properties)}
	result, response, err := c.api.ServersAPI.PatchServerProperties(ctx).
		XNetworkID(c.networkID).
		ServerId(id).
		ModelsPatchPropertiesRequest(input).
		Execute()
	if err != nil || result == nil {
		return nil, response, err
	}
	return fromWireProperties(result.Properties), response, nil
}

func (c *ServersClient) DeleteProperties(ctx context.Context, id string, keys ...string) (map[string]any, *http.Response, error) {
	input := ModelsDeletePropertiesRequest{Keys: keys}
	result, response, err := c.api.ServersAPI.DeleteServerProperties(ctx).
		XNetworkID(c.networkID).
		ServerId(id).
		ModelsDeletePropertiesRequest(input).
		Execute()
	if err != nil || result == nil {
		return nil, response, err
	}
	return fromWireProperties(result.Properties), response, nil
}
