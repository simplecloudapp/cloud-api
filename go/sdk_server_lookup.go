package simplecloud

import (
	"context"
	"errors"
	"fmt"
	"net/http"
	"os"
)

func (c *ServersClient) GetByID(ctx context.Context, id string) (*Server, *http.Response, error) {
	servers, response, err := c.List(ctx, &ServerQuery{IDs: []string{id}})
	if err != nil {
		return nil, response, err
	}
	for index := range servers {
		if servers[index].GetServerId() == id {
			return &servers[index], response, nil
		}
	}
	return nil, response, fmt.Errorf("%w: server %q", ErrNotFound, id)
}

func (c *ServersClient) GetByNumericalID(ctx context.Context, groupName string, numericalID int32) (*Server, *http.Response, error) {
	query := &ServerQuery{Names: []string{groupName}, NumericalIDs: []int32{numericalID}}
	servers, response, err := c.List(ctx, query)
	if err != nil {
		return nil, response, err
	}
	for index := range servers {
		server := &servers[index]
		if server.GetNumericalId() == numericalID && server.ServerGroup != nil && server.ServerGroup.GetName() == groupName {
			return server, response, nil
		}
	}
	return nil, response, fmt.Errorf("%w: server %s-%d", ErrNotFound, groupName, numericalID)
}

func (c *ServersClient) ListByGroup(ctx context.Context, groupName string) ([]Server, *http.Response, error) {
	return c.List(ctx, &ServerQuery{Names: []string{groupName}})
}

func (c *ServersClient) Current(ctx context.Context) (*Server, *http.Response, error) {
	id := os.Getenv("SIMPLECLOUD_UNIQUE_ID")
	if id == "" {
		return nil, nil, errors.New("simplecloud: SIMPLECLOUD_UNIQUE_ID is not set")
	}
	return c.GetByID(ctx, id)
}
