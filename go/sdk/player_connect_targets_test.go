package simplecloud

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
)

func TestConnectToGroupSendsGroupNameAndSelectionMode(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(writer http.ResponseWriter, request *http.Request) {
		if request.URL.Path != "/v0/players/connect" || request.URL.Query().Get("player_id") != "player-1" {
			t.Errorf("request = %s %s", request.Method, request.URL)
		}
		var body ModelsConnectPlayerRequest
		if err := json.NewDecoder(request.Body).Decode(&body); err != nil {
			t.Errorf("decode request: %v", err)
		}
		if body.GetServerGroupName() != "lobby" || body.GetSelectionMode() != "MIN_PLAYERS" || body.ServerGroupId != nil {
			t.Errorf("body = %#v", body)
		}
		writer.Header().Set("Content-Type", "application/json")
		_, _ = writer.Write([]byte(`{"success":true,"result":"SUCCESS","server_name":"lobby-1"}`))
	}))
	defer server.Close()

	client := newTestClient(t, server.URL)
	result, _, err := client.Players.ConnectToGroup(context.Background(), "player-1", "lobby", SelectMinPlayers)
	if err != nil {
		t.Fatal(err)
	}
	if result.GetResult() != ModelsConnectResult("SUCCESS") || result.GetServerName() != "lobby-1" {
		t.Fatalf("result = %#v", result)
	}
}
