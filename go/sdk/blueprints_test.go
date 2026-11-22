package simplecloud

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
)

func TestBlueprintReplaceSendsFullBlueprint(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(writer http.ResponseWriter, request *http.Request) {
		if request.Method != http.MethodPut || request.URL.Query().Get("blueprint_id") != "blueprint-1" {
			t.Errorf("request = %s %s", request.Method, request.URL)
		}
		var body ModelsUpdateBlueprintRequest
		if err := json.NewDecoder(request.Body).Decode(&body); err != nil {
			t.Errorf("decode request: %v", err)
		}
		if body.GetName() != "paper" {
			t.Errorf("name = %q", body.GetName())
		}
		writer.Header().Set("Content-Type", "application/json")
		_, _ = writer.Write([]byte(`{"blueprint_id":"blueprint-1","name":"paper"}`))
	}))
	defer server.Close()

	client := newTestClient(t, server.URL)
	name := "paper"
	blueprint, _, err := client.Blueprints.Replace(context.Background(), "blueprint-1", ModelsUpdateBlueprintRequest{Name: &name})
	if err != nil {
		t.Fatal(err)
	}
	if blueprint.GetBlueprintId() != "blueprint-1" || blueprint.GetName() != "paper" {
		t.Fatalf("blueprint = %#v", blueprint)
	}
}
