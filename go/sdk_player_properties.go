package simplecloud

import (
	"context"
	"net/http"
)

func (c *PlayersClient) PatchProperties(ctx context.Context, id string, properties map[string]string) (map[string]string, *http.Response, error) {
	input := ModelsUpdatePlayerPropertiesRequest{Properties: properties}
	result, response, err := c.api.PlayersAPI.V0PlayersPropertiesPatch(ctx).
		XNetworkID(c.networkID).
		XNetworkCredential(c.credential).
		PlayerId(id).
		ModelsUpdatePlayerPropertiesRequest(input).
		Execute()
	if err != nil || result == nil {
		return nil, response, err
	}
	return result.Properties, response, nil
}

func (c *PlayersClient) Properties(ctx context.Context, id string) (map[string]string, *http.Response, error) {
	result, response, err := c.api.PlayersAPI.V0PlayersPropertiesGet(ctx).
		XNetworkID(c.networkID).
		XNetworkCredential(c.credential).
		PlayerId(id).
		Execute()
	if err != nil || result == nil {
		return nil, response, err
	}
	return result.Properties, response, nil
}

func (c *PlayersClient) DeleteProperties(ctx context.Context, id string, keys ...string) (map[string]string, *http.Response, error) {
	input := ModelsDeletePlayerPropertiesRequest{Keys: keys}
	_, response, err := c.api.PlayersAPI.V0PlayersPropertiesDelete(ctx).
		XNetworkID(c.networkID).
		XNetworkCredential(c.credential).
		PlayerId(id).
		ModelsDeletePlayerPropertiesRequest(input).
		Execute()
	if err != nil {
		return nil, response, err
	}
	return c.Properties(ctx, id)
}
