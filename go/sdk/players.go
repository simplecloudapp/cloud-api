package simplecloud

import (
	"context"
	"net/http"

	"github.com/nats-io/nats.go"
)

type Player = ModelsPlayerResponse

type PlayersClient struct {
	authenticatedClient
	nats func() (*nats.Conn, error)
}

func (c *PlayersClient) GetByID(ctx context.Context, id string) (*Player, *http.Response, error) {
	return c.api.PlayersAPI.GetPlayer(ctx).
		XNetworkID(c.networkID).
		PlayerId(id).
		Execute()
}

func (c *PlayersClient) GetByName(ctx context.Context, name string) (*Player, *http.Response, error) {
	return c.api.PlayersAPI.GetPlayerByName(ctx).
		XNetworkID(c.networkID).
		Name(name).
		Execute()
}

func (c *PlayersClient) Online(ctx context.Context, server string) ([]Player, *http.Response, error) {
	request := c.api.PlayersAPI.ListOnlinePlayers(ctx).
		XNetworkID(c.networkID)
	if server != "" {
		request = request.Server(server)
	}
	result, response, err := request.Execute()
	if err != nil || result == nil {
		return nil, response, err
	}

	players := make([]Player, 0, len(result.Players))
	for index := range result.Players {
		player, err := mapWireModel[Player](&result.Players[index])
		if err != nil {
			return nil, response, err
		}
		players = append(players, *player)
	}
	return players, response, nil
}

func (c *PlayersClient) OnlineCount(ctx context.Context) (int32, *http.Response, error) {
	result, response, err := c.api.PlayersAPI.GetOnlinePlayerCount(ctx).
		XNetworkID(c.networkID).
		Execute()
	if err != nil || result == nil {
		return 0, response, err
	}
	return result.GetCount(), response, nil
}
