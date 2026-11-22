package simplecloud

import (
	"context"
	"os"
	"testing"

	playerv2 "buf.build/gen/go/simplecloud/player/protocolbuffers/go"
	"github.com/nats-io/nats.go"
	"google.golang.org/protobuf/proto"
)

func TestConnectDirectAsksTheProxyOverNATS(t *testing.T) {
	url := os.Getenv("NATS_TEST_URL")
	if url == "" {
		t.Skip("set NATS_TEST_URL to run broker integration tests")
	}
	proxy, err := nats.Connect(url)
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(proxy.Close)
	_, err = proxy.Subscribe(testNetworkID+".player.player-1.connect", func(message *nats.Msg) {
		request := &playerv2.ConnectPlayerRequest{}
		result := playerv2.ConnectResult_CONNECT_RESULT_SERVER_NOT_FOUND
		if proto.Unmarshal(message.Data, request) == nil && request.GetServerName() == "lobby-1" {
			result = playerv2.ConnectResult_CONNECT_RESULT_SUCCESS
		}
		payload, _ := proto.Marshal(&playerv2.ConnectPlayerResponse{Result: result})
		_ = message.Respond(payload)
	})
	if err != nil {
		t.Fatal(err)
	}
	if err := proxy.Flush(); err != nil {
		t.Fatal(err)
	}

	client := newNATSTestClient(t, url, nil)
	result, err := client.Players.ConnectDirect(context.Background(), "player-1", "lobby-1")
	if err != nil {
		t.Fatal(err)
	}
	if result != PlayerConnectSuccess {
		t.Fatalf("result = %v", result)
	}
}
