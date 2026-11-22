package simplecloud

import (
	"context"
	"fmt"
	"net/http"
)

// PlayerProfile is the stable facade name for a generated player-profile model.
type PlayerProfile = ModelsPlayerProfileResponse

// PlayerSession is the stable facade name for a generated player-session model.
type PlayerSession = ModelsPlayerSessionResponse

func (c *PlayersClient) Profiles(ctx context.Context, id string, limit, offset int32) ([]PlayerProfile, *http.Response, error) {
	request := c.api.PlayersAPI.ListPlayerProfiles(ctx).
		XNetworkID(c.networkID).
		PlayerId(id)
	if limit > 0 {
		request = request.Limit(limit)
	}
	if offset > 0 {
		request = request.Offset(offset)
	}
	result, response, err := request.Execute()
	if err != nil || result == nil {
		return nil, response, err
	}
	return result.Profiles, response, nil
}

func (c *PlayersClient) LatestProfile(ctx context.Context, id string) (*PlayerProfile, *http.Response, error) {
	return c.api.PlayersAPI.GetLatestPlayerProfile(ctx).
		XNetworkID(c.networkID).
		PlayerId(id).
		Execute()
}

func (c *PlayersClient) Sessions(ctx context.Context, id string, limit, offset int32) ([]PlayerSession, *http.Response, error) {
	request := c.api.PlayersAPI.ListPlayerSessions(ctx).
		XNetworkID(c.networkID).
		PlayerId(id)
	if limit > 0 {
		request = request.Limit(limit)
	}
	if offset > 0 {
		request = request.Offset(offset)
	}
	result, response, err := request.Execute()
	if err != nil || result == nil {
		return nil, response, err
	}
	return result.Sessions, response, nil
}

func (c *PlayersClient) CurrentSession(ctx context.Context, id string) (*PlayerSession, *http.Response, error) {
	result, response, err := c.api.PlayersAPI.GetCurrentSession(ctx).
		XNetworkID(c.networkID).
		PlayerId(id).
		Execute()
	if err != nil {
		return nil, response, err
	}
	if result == nil || result.Session == nil {
		return nil, response, fmt.Errorf("%w: current session of player %q", ErrNotFound, id)
	}
	return result.Session, response, nil
}
