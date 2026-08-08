package simplecloud

import (
	"context"
	"errors"
	"fmt"
	"net/http"
)

const maxPlayerOnlineTime = int64(1<<31 - 1)

func (c *PlayersClient) OnlineTime(ctx context.Context, id string) (int64, *http.Response, error) {
	result, response, err := c.api.PlayersAPI.V0PlayersOnlineTimeGet(ctx).
		XNetworkID(c.networkID).
		XNetworkCredential(c.credential).
		PlayerId(id).
		Execute()
	if err != nil || result == nil {
		return 0, response, err
	}
	return int64(result.GetOnlineTimeSeconds()), response, nil
}

func (c *PlayersClient) UpdateOnlineTime(ctx context.Context, id string, update func(int64) int64) (*Player, *http.Response, error) {
	if update == nil {
		return nil, nil, errors.New("simplecloud: online time update function must not be nil")
	}
	current, response, err := c.OnlineTime(ctx, id)
	if err != nil {
		return nil, response, err
	}
	return c.SetOnlineTime(ctx, id, update(current))
}

func (c *PlayersClient) AddOnlineTime(ctx context.Context, id string, seconds int64) (*Player, *http.Response, error) {
	if seconds < 0 {
		return nil, nil, errors.New("simplecloud: seconds must be non-negative")
	}
	return c.UpdateOnlineTime(ctx, id, func(current int64) int64 { return current + seconds })
}

func (c *PlayersClient) RemoveOnlineTime(ctx context.Context, id string, seconds int64) (*Player, *http.Response, error) {
	if seconds < 0 {
		return nil, nil, errors.New("simplecloud: seconds must be non-negative")
	}
	return c.UpdateOnlineTime(ctx, id, func(current int64) int64 {
		if current <= seconds {
			return 0
		}
		return current - seconds
	})
}

func (c *PlayersClient) SetOnlineTime(ctx context.Context, id string, seconds int64) (*Player, *http.Response, error) {
	if seconds < 0 || seconds > maxPlayerOnlineTime {
		return nil, nil, fmt.Errorf("simplecloud: online time must be between 0 and %d", maxPlayerOnlineTime)
	}
	value := int32(seconds)
	input := ModelsPatchPlayerRequest{OnlineTimeSeconds: &value}
	return c.api.PlayersAPI.V0PlayersPatch(ctx).
		XNetworkID(c.networkID).
		XNetworkCredential(c.credential).
		PlayerId(id).
		ModelsPatchPlayerRequest(input).
		Execute()
}
