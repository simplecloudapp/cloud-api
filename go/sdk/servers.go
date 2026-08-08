package simplecloud

import (
	"context"
	"net/http"
)

// Server is the stable facade name for a generated server model.
type Server = ModelsServerSummary

type ServerQuery struct {
	IDs                 []string
	GroupIDs            []string
	States              []string
	ServerhostIDs       []string
	PersistentServerIDs []string
	Types               []string
	Names               []string
	Tags                []string
	NumericalIDs        []int32
	SortBy              string
	SortOrder           string
	Limit               *int32
	Offset              *int32
}

type ServersClient struct{ authenticatedClient }

func (c *ServersClient) List(ctx context.Context, query *ServerQuery) ([]Server, *http.Response, error) {
	request := c.api.ServersAPI.V0ServersGet(ctx).
		XNetworkID(c.networkID).
		XNetworkCredential(c.credential)
	if query != nil {
		if value := stringQuery(query.IDs); value != "" {
			request = request.ServerId(value)
		}
		if value := stringQuery(query.GroupIDs); value != "" {
			request = request.ServerGroupId(value)
		}
		if value := stringQuery(query.States); value != "" {
			request = request.State(value)
		}
		if value := stringQuery(query.ServerhostIDs); value != "" {
			request = request.ServerhostId(value)
		}
		if value := stringQuery(query.PersistentServerIDs); value != "" {
			request = request.PersistentServerId(value)
		}
		if value := stringQuery(query.Types); value != "" {
			request = request.Type_(value)
		}
		if value := stringQuery(query.Names); value != "" {
			request = request.Name(value)
		}
		if value := stringQuery(query.Tags); value != "" {
			request = request.Tags(value)
		}
		if value := int32Query(query.NumericalIDs); value != "" {
			request = request.NumericalId(value)
		}
		if query.SortBy != "" {
			request = request.SortBy(query.SortBy)
		}
		if query.SortOrder != "" {
			request = request.SortOrder(query.SortOrder)
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
	return result.Servers, response, nil
}
