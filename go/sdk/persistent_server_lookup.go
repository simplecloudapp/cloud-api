package simplecloud

import (
	"context"
	"fmt"
	"net/http"
)

func (c *PersistentServersClient) GetByID(ctx context.Context, id string) (*PersistentServer, *http.Response, error) {
	servers, response, err := c.List(ctx, &PersistentServerQuery{IDs: []string{id}})
	if err != nil {
		return nil, response, err
	}
	for index := range servers {
		if servers[index].GetPersistentServerId() == id {
			return &servers[index], response, nil
		}
	}
	return nil, response, fmt.Errorf("%w: persistent server %q", ErrNotFound, id)
}

func (c *PersistentServersClient) GetByName(ctx context.Context, name string) (*PersistentServer, *http.Response, error) {
	servers, response, err := c.List(ctx, &PersistentServerQuery{Names: []string{name}})
	if err != nil {
		return nil, response, err
	}
	for index := range servers {
		if servers[index].GetName() == name {
			return &servers[index], response, nil
		}
	}
	return nil, response, fmt.Errorf("%w: persistent server %q", ErrNotFound, name)
}
