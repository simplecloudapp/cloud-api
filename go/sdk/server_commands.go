package simplecloud

import (
	"context"
	"net/http"
)

func (c *ServersClient) SendCommand(ctx context.Context, id, command string) (*ModelsSendCommandResponse, *http.Response, error) {
	input := ModelsSendCommandRequest{Command: &command}
	return c.api.ServersAPI.SendCommand(ctx).
		XNetworkID(c.networkID).
		ServerId(id).
		ModelsSendCommandRequest(input).
		Execute()
}
