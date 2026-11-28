package simplecloud

import (
	"context"
	"fmt"
	"time"

	playerv2 "buf.build/gen/go/simplecloud/player/protocolbuffers/go"
	"google.golang.org/protobuf/proto"
)

const directConnectTimeout = 30 * time.Second

func (c *PlayersClient) ConnectDirect(ctx context.Context, id, serverName string) (PlayerConnectResult, error) {
	connection, err := c.nats()
	if err != nil {
		return PlayerConnectFailed, err
	}
	payload, err := proto.Marshal(&playerv2.ConnectPlayerRequest{PlayerId: id, ServerName: serverName})
	if err != nil {
		return PlayerConnectFailed, fmt.Errorf("simplecloud: encode connect request: %w", err)
	}
	if _, ok := ctx.Deadline(); !ok {
		var cancel context.CancelFunc
		ctx, cancel = context.WithTimeout(ctx, directConnectTimeout)
		defer cancel()
	}
	message, err := connection.RequestWithContext(ctx, c.networkID+".player."+id+".connect", payload)
	if err != nil {
		return PlayerConnectFailed, fmt.Errorf("simplecloud: connect player %q: %w", id, err)
	}
	response := &playerv2.ConnectPlayerResponse{}
	if err := proto.Unmarshal(message.Data, response); err != nil {
		return PlayerConnectFailed, fmt.Errorf("simplecloud: decode connect response: %w", err)
	}
	return response.GetResult(), nil
}
