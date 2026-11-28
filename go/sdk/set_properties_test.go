package simplecloud

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"reflect"
	"testing"
)

func TestSetPropertiesReplacesAllProperties(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(writer http.ResponseWriter, request *http.Request) {
		if request.Method != http.MethodPut || request.URL.Path != "/v0/server-groups/properties" {
			t.Errorf("request = %s %s", request.Method, request.URL)
		}
		var body map[string]map[string]any
		if err := json.NewDecoder(request.Body).Decode(&body); err != nil {
			t.Errorf("decode request: %v", err)
		}
		if !reflect.DeepEqual(body["properties"], map[string]any{"motd": "hello", "slots": float64(20)}) {
			t.Errorf("properties = %v", body["properties"])
		}
		writer.Header().Set("Content-Type", "application/json")
		_, _ = writer.Write([]byte(`{"properties":{"motd":"hello","slots":20,"legacy":null}}`))
	}))
	defer server.Close()

	client := newTestClient(t, server.URL)
	properties, _, err := client.Groups.SetProperties(context.Background(), "group-1", map[string]any{"motd": "hello", "slots": 20})
	if err != nil {
		t.Fatal(err)
	}
	want := map[string]any{"motd": "hello", "slots": float64(20), "legacy": nil}
	if !reflect.DeepEqual(properties, want) {
		t.Fatalf("properties = %v", properties)
	}
}
