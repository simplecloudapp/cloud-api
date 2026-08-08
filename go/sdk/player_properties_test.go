package simplecloud

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"reflect"
	"testing"
)

func TestPlayerDeletePropertiesReturnsRemainingProperties(t *testing.T) {
	methods := make([]string, 0, 2)
	server := httptest.NewServer(http.HandlerFunc(func(writer http.ResponseWriter, request *http.Request) {
		methods = append(methods, request.Method)
		writer.Header().Set("Content-Type", "application/json")
		switch request.Method {
		case http.MethodDelete:
			var body ModelsDeletePlayerPropertiesRequest
			if err := json.NewDecoder(request.Body).Decode(&body); err != nil {
				t.Errorf("decode request: %v", err)
			}
			if !reflect.DeepEqual(body.Keys, []string{"rank"}) {
				t.Errorf("keys = %v", body.Keys)
			}
			_, _ = writer.Write([]byte(`{"message":"deleted","player_id":"player-1"}`))
		case http.MethodGet:
			_, _ = writer.Write([]byte(`{"player_id":"player-1","properties":{"locale":"en_US"}}`))
		default:
			writer.WriteHeader(http.StatusMethodNotAllowed)
		}
	}))
	defer server.Close()

	client := newTestClient(t, server.URL)
	properties, _, err := client.Players.DeleteProperties(context.Background(), "player-1", "rank")
	if err != nil {
		t.Fatal(err)
	}
	if !reflect.DeepEqual(methods, []string{http.MethodDelete, http.MethodGet}) {
		t.Fatalf("methods = %v", methods)
	}
	if !reflect.DeepEqual(properties, map[string]string{"locale": "en_US"}) {
		t.Fatalf("properties = %v", properties)
	}
}
