package simplecloud

import (
	"context"
	"net/http"
)

func (c *ServersClient) Stop(ctx context.Context, id string) (*ModelsStopServerResponse, *http.Response, error) {
	return c.api.ServersAPI.V0ServersDelete(ctx).
		XNetworkID(c.networkID).
		XNetworkCredential(c.credential).
		ServerId(id).
		Execute()
}

func (c *ServersClient) Update(ctx context.Context, id string, input ModelsPatchServerRequest) (*Server, *http.Response, error) {
	result, response, err := c.api.ServersAPI.V0ServersPatch(ctx).
		XNetworkID(c.networkID).
		XNetworkCredential(c.credential).
		ServerId(id).
		ModelsPatchServerRequest(input).
		Execute()
	if err != nil || result == nil {
		return nil, response, err
	}
	return result.Server, response, nil
}

func (c *ServersClient) PatchProperties(ctx context.Context, id string, properties map[string]any) (map[string]any, *http.Response, error) {
	input := ModelsPatchPropertiesRequest{Properties: properties}
	result, response, err := c.api.ServersAPI.V0ServersPropertiesPatch(ctx).
		XNetworkID(c.networkID).
		XNetworkCredential(c.credential).
		ServerId(id).
		ModelsPatchPropertiesRequest(input).
		Execute()
	if err != nil || result == nil {
		return nil, response, err
	}
	return result.Properties, response, nil
}

func (c *ServersClient) DeleteProperties(ctx context.Context, id string, keys ...string) (map[string]any, *http.Response, error) {
	input := ModelsDeletePropertiesRequest{Keys: keys}
	result, response, err := c.api.ServersAPI.V0ServersPropertiesDelete(ctx).
		XNetworkID(c.networkID).
		XNetworkCredential(c.credential).
		ServerId(id).
		ModelsDeletePropertiesRequest(input).
		Execute()
	if err != nil || result == nil {
		return nil, response, err
	}
	return result.Properties, response, nil
}
