package simplecloud

import (
	"context"
	"net/http"
)

func (c *PlayersClient) ResetOnlineTime(ctx context.Context, id string) (*Player, *http.Response, error) {
	return c.SetOnlineTime(ctx, id, 0)
}

func (c *PlayersClient) ResetFirstSeen(ctx context.Context, id string) (*Player, *http.Response, error) {
	reset := true
	return c.patch(ctx, id, ModelsPatchPlayerRequest{ResetFirstSeen: &reset})
}

func (c *PlayersClient) ResetLastSeen(ctx context.Context, id string) (*Player, *http.Response, error) {
	reset := true
	return c.patch(ctx, id, ModelsPatchPlayerRequest{ResetLastSeen: &reset})
}

func (c *PlayersClient) patch(ctx context.Context, id string, input ModelsPatchPlayerRequest) (*Player, *http.Response, error) {
	return c.api.PlayersAPI.PatchPlayer(ctx).
		XNetworkID(c.networkID).
		PlayerId(id).
		ModelsPatchPlayerRequest(input).
		Execute()
}
