package simplecloud

import (
	"context"
	"net/http"
)

func (c *PersistentServersClient) Create(ctx context.Context, input ModelsCreatePersistentServerRequest) (*PersistentServer, *http.Response, error) {
	result, response, err := c.api.PersistentServersAPI.V0PersistentServersPost(ctx).
		XNetworkID(c.networkID).
		XNetworkCredential(c.credential).
		ModelsCreatePersistentServerRequest(input).
		Execute()
	return mapWireResponse[PersistentServer](result, response, err)
}

func (c *PersistentServersClient) Update(ctx context.Context, id string, input ModelsPatchPersistentServerRequest) (*PersistentServer, *http.Response, error) {
	result, response, err := c.api.PersistentServersAPI.V0PersistentServersPatch(ctx).
		XNetworkID(c.networkID).
		XNetworkCredential(c.credential).
		PersistentServerId(id).
		ModelsPatchPersistentServerRequest(input).
		Execute()
	return mapWireResponse[PersistentServer](result, response, err)
}

func (c *PersistentServersClient) Delete(ctx context.Context, id string) (*ModelsDeletePersistentServerResponse, *http.Response, error) {
	return c.api.PersistentServersAPI.V0PersistentServersDelete(ctx).
		XNetworkID(c.networkID).
		XNetworkCredential(c.credential).
		PersistentServerId(id).
		Execute()
}

func (c *PersistentServersClient) PatchProperties(ctx context.Context, id string, properties map[string]any) (map[string]any, *http.Response, error) {
	input := ModelsPatchPropertiesRequest{Properties: properties}
	result, response, err := c.api.PersistentServersAPI.V0PersistentServersPropertiesPatch(ctx).
		XNetworkID(c.networkID).
		XNetworkCredential(c.credential).
		PersistentServerId(id).
		ModelsPatchPropertiesRequest(input).
		Execute()
	if err != nil || result == nil {
		return nil, response, err
	}
	return result.Properties, response, nil
}

func (c *PersistentServersClient) DeleteProperties(ctx context.Context, id string, keys ...string) (map[string]any, *http.Response, error) {
	input := ModelsDeletePropertiesRequest{Keys: keys}
	result, response, err := c.api.PersistentServersAPI.V0PersistentServersPropertiesDelete(ctx).
		XNetworkID(c.networkID).
		XNetworkCredential(c.credential).
		PersistentServerId(id).
		ModelsDeletePropertiesRequest(input).
		Execute()
	if err != nil || result == nil {
		return nil, response, err
	}
	return result.Properties, response, nil
}
