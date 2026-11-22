package simplecloud

import (
	"context"
	"net/http"
)

func (c *BlueprintsClient) Create(ctx context.Context, input ModelsCreateBlueprintRequest) (*Blueprint, *http.Response, error) {
	result, response, err := c.api.BlueprintsAPI.CreateBlueprint(ctx).
		XNetworkID(c.networkID).
		ModelsCreateBlueprintRequest(input).
		Execute()
	return mapWireResponse[Blueprint](result, response, err)
}

func (c *BlueprintsClient) Update(ctx context.Context, id string, input ModelsPatchBlueprintRequest) (*Blueprint, *http.Response, error) {
	result, response, err := c.api.BlueprintsAPI.PatchBlueprint(ctx).
		XNetworkID(c.networkID).
		BlueprintId(id).
		ModelsPatchBlueprintRequest(input).
		Execute()
	return mapWireResponse[Blueprint](result, response, err)
}

func (c *BlueprintsClient) Replace(ctx context.Context, id string, input ModelsUpdateBlueprintRequest) (*Blueprint, *http.Response, error) {
	result, response, err := c.api.BlueprintsAPI.UpdateBlueprint(ctx).
		XNetworkID(c.networkID).
		BlueprintId(id).
		ModelsUpdateBlueprintRequest(input).
		Execute()
	return mapWireResponse[Blueprint](result, response, err)
}

func (c *BlueprintsClient) Delete(ctx context.Context, id string) (*ModelsDeleteBlueprintResponse, *http.Response, error) {
	return c.api.BlueprintsAPI.DeleteBlueprint(ctx).
		XNetworkID(c.networkID).
		BlueprintId(id).
		Execute()
}
