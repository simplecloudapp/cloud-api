package simplecloud

import (
	"context"
	"net/http"
)

// PersistentServer is the stable facade name for a generated persistent-server model.
type PersistentServer = ModelsPersistentServerSummary

type PersistentServerQuery struct {
	IDs           []string
	Names         []string
	Types         []string
	Tags          []string
	Active        *bool
	SourceTypes   []string
	BlueprintIDs  []string
	ServerhostIDs []string
	Limit         *int32
	Offset        *int32
}

type PersistentServersClient struct{ authenticatedClient }

func (c *PersistentServersClient) List(ctx context.Context, query *PersistentServerQuery) ([]PersistentServer, *http.Response, error) {
	request := c.api.PersistentServersAPI.V0PersistentServersGet(ctx).
		XNetworkID(c.networkID).
		XNetworkCredential(c.credential)
	if query != nil {
		if value := stringQuery(query.IDs); value != "" {
			request = request.PersistentServerId(value)
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
		if value := stringQuery(query.ServerhostIDs); value != "" {
			request = request.ServerhostId(value)
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
	return result.PersistentServers, response, nil
}
