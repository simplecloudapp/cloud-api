package simplecloud

import (
	"context"
	"fmt"
	"net/http"
)

type Blueprint = ModelsBlueprintSummary

type BlueprintsClient struct{ authenticatedClient }

func (c *BlueprintsClient) List(ctx context.Context) ([]Blueprint, *http.Response, error) {
	result, response, err := c.api.BlueprintsAPI.ListBlueprints(ctx).
		XNetworkID(c.networkID).
		Execute()
	if err != nil || result == nil {
		return nil, response, err
	}
	return result.Blueprints, response, nil
}

func (c *BlueprintsClient) GetByID(ctx context.Context, id string) (*Blueprint, *http.Response, error) {
	blueprints, response, err := c.List(ctx)
	if err != nil {
		return nil, response, err
	}
	for index := range blueprints {
		if blueprints[index].GetBlueprintId() == id {
			return &blueprints[index], response, nil
		}
	}
	return nil, response, fmt.Errorf("%w: blueprint %q", ErrNotFound, id)
}

func (c *BlueprintsClient) GetByName(ctx context.Context, name string) (*Blueprint, *http.Response, error) {
	blueprints, response, err := c.List(ctx)
	if err != nil {
		return nil, response, err
	}
	for index := range blueprints {
		if blueprints[index].GetName() == name {
			return &blueprints[index], response, nil
		}
	}
	return nil, response, fmt.Errorf("%w: blueprint %q", ErrNotFound, name)
}
