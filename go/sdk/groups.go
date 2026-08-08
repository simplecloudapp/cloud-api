package simplecloud

import (
	"context"
	"fmt"
	"net/http"
)

// Group is the stable facade name for a generated server-group model.
type Group = ModelsServerGroupSummary

type GroupQuery struct {
	IDs          []string
	Names        []string
	Types        []string
	Tags         []string
	Active       *bool
	SourceTypes  []string
	BlueprintIDs []string
	Limit        *int32
	Offset       *int32
}

type GroupsClient struct{ authenticatedClient }

func (c *GroupsClient) List(ctx context.Context, query *GroupQuery) ([]Group, *http.Response, error) {
	request := c.api.ServerGroupsAPI.V0ServerGroupsGet(ctx).
		XNetworkID(c.networkID).
		XNetworkCredential(c.credential)
	if query != nil {
		if value := stringQuery(query.IDs); value != "" {
			request = request.ServerGroupId(value)
		}
		if value := stringQuery(query.Names); value != "" {
			request = request.Name(value)
		}
		if value := stringQuery(query.Types); value != "" {
			request = request.Type_(value)
		}
		if value := stringQuery(query.Tags); value != "" {
			request = request.Tags(value)
		}
		if query.Active != nil {
			request = request.Active(*query.Active)
		}
		if value := stringQuery(query.SourceTypes); value != "" {
			request = request.SourceType(value)
		}
		if value := stringQuery(query.BlueprintIDs); value != "" {
			request = request.BlueprintId(value)
		}
		if query.Limit != nil {
			request = request.Limit(*query.Limit)
		}
		if query.Offset != nil {
			request = request.Offset(*query.Offset)
		}
	}
	result, response, err := request.Execute()
	if err != nil || result == nil {
		return nil, response, err
	}
	return result.ServerGroups, response, nil
}

func (c *GroupsClient) GetByID(ctx context.Context, id string) (*Group, *http.Response, error) {
	groups, response, err := c.List(ctx, &GroupQuery{IDs: []string{id}})
	if err != nil {
		return nil, response, err
	}
	for index := range groups {
		if groups[index].GetServerGroupId() == id {
			return &groups[index], response, nil
		}
	}
	return nil, response, fmt.Errorf("%w: group %q", ErrNotFound, id)
}

func (c *GroupsClient) GetByName(ctx context.Context, name string) (*Group, *http.Response, error) {
	groups, response, err := c.List(ctx, &GroupQuery{Names: []string{name}})
	if err != nil {
		return nil, response, err
	}
	for index := range groups {
		if groups[index].GetName() == name {
			return &groups[index], response, nil
		}
	}
	return nil, response, fmt.Errorf("%w: group %q", ErrNotFound, name)
}
