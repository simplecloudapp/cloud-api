package simplecloud

import (
	"net/http"
	"strings"
	"sync"
	"time"

	"github.com/nats-io/nats.go"
	"github.com/simplecloudapp/cloud-api/go/generated"
)

type Client struct {
	Raw               *generated.APIClient
	Groups            *GroupsClient
	Servers           *ServersClient
	PersistentServers *PersistentServersClient
	Players           *PlayersClient
	Blueprints        *BlueprintsClient
	Stats             *StatsClient
	Events            *EventsClient

	options Options
	natsMu  sync.Mutex
	nats    *nats.Conn
}

// NewClient creates a client. Its NATS connection is opened lazily.
func NewClient(options Options) (*Client, error) {
	options = options.withDefaults()
	controllerURL, err := parseControllerURL(options.ControllerURL)
	if err != nil {
		return nil, err
	}

	config := generated.NewConfiguration()
	config.Servers = generated.ServerConfigurations{{URL: controllerURL}}
	config.HTTPClient = options.HTTPClient
	if config.HTTPClient == nil {
		config.HTTPClient = &http.Client{Timeout: 10 * time.Second}
	}
	if options.NetworkSecret != "" {
		config.AddDefaultHeader("X-Network-Credential", options.NetworkSecret)
	}
	if strings.TrimSpace(options.Component) != "" {
		config.AddDefaultHeader("X-SC-Component", options.Component)
	}

	raw := generated.NewAPIClient(config)
	client := &Client{Raw: raw, options: options}
	api := authenticatedClient{api: raw, networkID: options.NetworkID}
	client.Groups = &GroupsClient{authenticatedClient: api}
	client.Servers = &ServersClient{authenticatedClient: api}
	client.PersistentServers = &PersistentServersClient{authenticatedClient: api}
	client.Players = &PlayersClient{authenticatedClient: api, nats: client.NATS}
	client.Blueprints = &BlueprintsClient{authenticatedClient: api}
	client.Stats = &StatsClient{authenticatedClient: api}
	client.Events = &EventsClient{client: client}
	return client, nil
}

// NewClientFromEnv creates a client using DefaultOptions.
func NewClientFromEnv() (*Client, error) { return NewClient(DefaultOptions()) }

// NetworkID returns the network this client authenticates against.
func (c *Client) NetworkID() string { return c.options.NetworkID }

type authenticatedClient struct {
	api       *generated.APIClient
	networkID string
}
