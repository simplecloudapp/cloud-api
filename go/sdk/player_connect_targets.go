package simplecloud

import (
	"context"
	"net/http"
)

// ServerSelectionMode picks a server when a player connects to a group.
type ServerSelectionMode string

const (
	SelectRandom     ServerSelectionMode = "RANDOM"
	SelectMinPlayers ServerSelectionMode = "MIN_PLAYERS"
	SelectMaxPlayers ServerSelectionMode = "MAX_PLAYERS"
	SelectFirst      ServerSelectionMode = "FIRST"
)

func (c *PlayersClient) ConnectToGroup(ctx context.Context, id, groupName string, mode ServerSelectionMode) (*ModelsConnectPlayerResponse, *http.Response, error) {
	input := ModelsConnectPlayerRequest{ServerGroupName: &groupName}
	if mode != "" {
		selection := string(mode)
		input.SelectionMode = &selection
	}
	return c.Connect(ctx, id, input)
}

func (c *PlayersClient) ConnectToPersistentServer(ctx context.Context, id, persistentServerID string) (*ModelsConnectPlayerResponse, *http.Response, error) {
	return c.Connect(ctx, id, ModelsConnectPlayerRequest{PersistentServerId: &persistentServerID})
}
