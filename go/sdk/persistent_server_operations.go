package simplecloud

import (
	"context"
	"net/http"
)

func (c *PersistentServersClient) Create(ctx context.Context, input ModelsCreatePersistentServerRequest) (*PersistentServer, *http.Response, error) {
	result, response, err := c.api.PersistentServersAPI.CreatePersistentServer(ctx).
		XNetworkID(c.networkID).
		ModelsCreatePersistentServerRequest(input).
		Execute()
	return mapWireResponse[PersistentServer](result, response, err)
}

func (c *PersistentServersClient) Update(ctx context.Context, id string, input ModelsPatchPersistentServerRequest) (*PersistentServer, *http.Response, error) {
	result, response, err := c.api.PersistentServersAPI.PatchPersistentServer(ctx).
		XNetworkID(c.networkID).
		PersistentServerId(id).
		ModelsPatchPersistentServerRequest(input).
		Execute()
	return mapWireResponse[PersistentServer](result, response, err)
}

func (c *PersistentServersClient) Delete(ctx context.Context, id string) (*ModelsDeletePersistentServerResponse, *http.Response, error) {
	return c.api.PersistentServersAPI.DeletePersistentServer(ctx).
		XNetworkID(c.networkID).
		PersistentServerId(id).
		Execute()
}

func (c *PersistentServersClient) PatchProperties(ctx context.Context, id string, properties map[string]any) (map[string]any, *http.Response, error) {
	input := ModelsPatchPropertiesRequest{Properties: toWireProperties(properties)}
	result, response, err := c.api.PersistentServersAPI.PatchPersistentServerProperties(ctx).
		XNetworkID(c.networkID).
		PersistentServerId(id).
		ModelsPatchPropertiesRequest(input).
		Execute()
	if err != nil || result == nil {
		return nil, response, err
	}
	return fromWireProperties(result.Properties), response, nil
}

func (c *PersistentServersClient) DeleteProperties(ctx context.Context, id string, keys ...string) (map[string]any, *http.Response, error) {
	input := ModelsDeletePropertiesRequest{Keys: keys}
	result, response, err := c.api.PersistentServersAPI.DeletePersistentServerProperties(ctx).
		XNetworkID(c.networkID).
		PersistentServerId(id).
		ModelsDeletePropertiesRequest(input).
		Execute()
	if err != nil || result == nil {
		return nil, response, err
	}
	return fromWireProperties(result.Properties), response, nil
}
