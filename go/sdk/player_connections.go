package simplecloud

import (
	"context"
	"net/http"
)

func (c *PlayersClient) Connect(ctx context.Context, id string, input ModelsConnectPlayerRequest) (*ModelsConnectPlayerResponse, *http.Response, error) {
	return c.api.PlayersAPI.ConnectPlayer(ctx).
		XNetworkID(c.networkID).
		PlayerId(id).
		ModelsConnectPlayerRequest(input).
		Execute()
}

func (c *PlayersClient) Kick(ctx context.Context, id, reason string) (*ModelsKickPlayerResponse, *http.Response, error) {
	input := ModelsKickPlayerRequest{Reason: &reason}
	return c.api.PlayersAPI.KickPlayer(ctx).
		XNetworkID(c.networkID).
		PlayerId(id).
		ModelsKickPlayerRequest(input).
		Execute()
}
