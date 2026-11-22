package simplecloud

import (
	"context"
	"net/http"
)

type Stats = ModelsCloudStats

type StatsClient struct{ authenticatedClient }

func (c *StatsClient) Get(ctx context.Context) (*Stats, *http.Response, error) {
	return c.api.StatsAPI.GetStats(ctx).
		XNetworkID(c.networkID).
		Execute()
}
