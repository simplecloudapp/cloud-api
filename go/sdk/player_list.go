package simplecloud

import (
	"context"
	"net/http"
	"time"
)

type PlayerQuery struct {
	IDs                  []string
	Online               *bool
	Server               string
	Proxy                string
	Search               string
	FirstSeenFrom        *time.Time
	FirstSeenTo          *time.Time
	LastSeenFrom         *time.Time
	LastSeenTo           *time.Time
	OnlineTimeSecondsMin *int32
	OnlineTimeSecondsMax *int32
	SortBy               string
	SortOrder            string
	Limit                *int32
	Offset               *int32
}

func (c *PlayersClient) List(ctx context.Context, query *PlayerQuery) ([]Player, *http.Response, error) {
	request := c.api.PlayersAPI.ListPlayers(ctx).
		XNetworkID(c.networkID)
	if query != nil {
		if len(query.IDs) > 0 {
			request = request.PlayerId(query.IDs)
		}
		if query.Online != nil {
			request = request.Online(*query.Online)
		}
		if query.Server != "" {
			request = request.Server(query.Server)
		}
		if query.Proxy != "" {
			request = request.Proxy(query.Proxy)
		}
		if query.Search != "" {
			request = request.Search(query.Search)
		}
		if query.FirstSeenFrom != nil {
			request = request.FirstSeenFrom(*query.FirstSeenFrom)
		}
		if query.FirstSeenTo != nil {
			request = request.FirstSeenTo(*query.FirstSeenTo)
		}
		if query.LastSeenFrom != nil {
			request = request.LastSeenFrom(*query.LastSeenFrom)
		}
		if query.LastSeenTo != nil {
			request = request.LastSeenTo(*query.LastSeenTo)
		}
		if query.OnlineTimeSecondsMin != nil {
			request = request.OnlineTimeSecondsMin(*query.OnlineTimeSecondsMin)
		}
		if query.OnlineTimeSecondsMax != nil {
			request = request.OnlineTimeSecondsMax(*query.OnlineTimeSecondsMax)
		}
		if query.SortBy != "" {
			request = request.SortBy(query.SortBy)
		}
		if query.SortOrder != "" {
			request = request.SortOrder(query.SortOrder)
		}
		if query.Limit != nil {
			request = request.Limit(*query.Limit)
		}
		if query.Offset != nil {
			request = request.Offset(*query.Offset)
		}
	}
	result, response, err := request.Execute()
	if err != nil || result == nil {
		return nil, response, err
	}
	return result.Players, response, nil
}
