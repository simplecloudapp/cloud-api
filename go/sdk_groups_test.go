package simplecloud

import (
	"context"
	"net/http"
	"net/http/httptest"
	"testing"
)

func TestGroupsListAppliesCredentialsAndFilters(t *testing.T) {
	active := false
	limit := int32(25)
	server := httptest.NewServer(http.HandlerFunc(func(writer http.ResponseWriter, request *http.Request) {
		if request.URL.Path != "/v0/server-groups" {
			t.Errorf("path = %q", request.URL.Path)
		}
		if request.Header.Get("X-Network-ID") != "network-1" {
			t.Errorf("network header = %q", request.Header.Get("X-Network-ID"))
		}
		if request.Header.Get("X-Network-Credential") != "secret-1" {
			t.Errorf("credential header = %q", request.Header.Get("X-Network-Credential"))
		}
		if request.Header.Get("X-SC-Component") != "tests" {
			t.Errorf("component header = %q", request.Header.Get("X-SC-Component"))
		}
		query := request.URL.Query()
		if query.Get("name") != "lobby,proxy" || query.Get("active") != "false" || query.Get("limit") != "25" {
			t.Errorf("unexpected query: %v", query)
		}
		writer.Header().Set("Content-Type", "application/json")
		_, _ = writer.Write([]byte(`{"count":1,"server_groups":[{"server_group_id":"group-1","name":"lobby"}]}`))
	}))
	defer server.Close()

	client := newTestClient(t, server.URL)
	groups, response, err := client.Groups.List(context.Background(), &GroupQuery{
		Names:  []string{"lobby", "proxy"},
		Active: &active,
		Limit:  &limit,
	})
	if err != nil {
		t.Fatal(err)
	}
	if response.StatusCode != http.StatusOK {
		t.Fatalf("status = %d", response.StatusCode)
	}
	if len(groups) != 1 || groups[0].GetServerGroupId() != "group-1" {
		t.Fatalf("unexpected groups: %#v", groups)
	}
}
