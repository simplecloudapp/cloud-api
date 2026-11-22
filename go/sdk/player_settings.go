package simplecloud

import (
	"context"
	"net/http"
)

type PlayerClientSettings = ModelsPlayerClientSettingsResponse

func (c *PlayersClient) ClientSettings(ctx context.Context, id string) (*PlayerClientSettings, *http.Response, error) {
	return c.api.PlayersAPI.GetClientSettings(ctx).
		XNetworkID(c.networkID).
		PlayerId(id).
		Execute()
}

func (c *PlayersClient) UpdateClientSettings(ctx context.Context, id string, input ModelsUpdateClientSettingsRequest) (*PlayerClientSettings, *http.Response, error) {
	result, response, err := c.api.PlayersAPI.UpdateClientSettings(ctx).
		XNetworkID(c.networkID).
		PlayerId(id).
		ModelsUpdateClientSettingsRequest(input).
		Execute()
	if err != nil || result == nil {
		return nil, response, err
	}
	return result.Settings, response, nil
}
