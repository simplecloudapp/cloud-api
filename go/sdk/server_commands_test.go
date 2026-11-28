package simplecloud

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
)

func TestSendCommandTargetsServer(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(writer http.ResponseWriter, request *http.Request) {
		if request.URL.Path != "/v0/servers/command" || request.URL.Query().Get("server_id") != "server-1" {
			t.Errorf("request = %s %s", request.Method, request.URL)
		}
		var body ModelsSendCommandRequest
		if err := json.NewDecoder(request.Body).Decode(&body); err != nil {
			t.Errorf("decode request: %v", err)
		}
		if body.GetCommand() != "say hi" {
			t.Errorf("command = %q", body.GetCommand())
		}
		writer.Header().Set("Content-Type", "application/json")
		_, _ = writer.Write([]byte(`{"server_id":"server-1","command":"say hi"}`))
	}))
	defer server.Close()

	client := newTestClient(t, server.URL)
	result, _, err := client.Servers.SendCommand(context.Background(), "server-1", "say hi")
	if err != nil {
		t.Fatal(err)
	}
	if result.GetServerId() != "server-1" {
		t.Fatalf("result = %#v", result)
	}
}
