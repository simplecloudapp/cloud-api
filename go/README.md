# SimpleCloud Go SDK

Go client for the SimpleCloud controller. Manage groups, servers, persistent servers, blueprints and players, and subscribe to network events.

## Install

```sh
go get github.com/simplecloudapp/cloud-api/go
```

Requires Go 1.26 or newer.

## Quick start

```go
package main

import (
	"context"
	"fmt"
	"log"

	simplecloud "github.com/simplecloudapp/cloud-api/go"
)

func main() {
	client, err := simplecloud.NewClientFromEnv()
	if err != nil {
		log.Fatal(err)
	}
	defer client.Close()

	groups, _, err := client.Groups.List(context.Background(), nil)
	if err != nil {
		log.Fatal(err)
	}
	for _, group := range groups {
		fmt.Println(group.GetName())
	}
}
```

Set `SIMPLECLOUD_NETWORK_ID` and `SIMPLECLOUD_NETWORK_SECRET` before running it.

## Clients

| Field | Covers |
|---|---|
| `client.Groups` | groups, start queue, rolling restarts, workflows, conversion to a persistent server |
| `client.Servers` | servers, commands, workflows, stop |
| `client.PersistentServers` | persistent servers, workflows, conversion to a group |
| `client.Blueprints` | blueprints |
| `client.Players` | online players, profiles, sessions, properties, settings, connect and kick |
| `client.Stats` | network stats |
| `client.Events` | NATS events for all of the above |
| `client.Raw` | every controller endpoint, generated from the OpenAPI spec |

## Docs

- [Configuration](docs/configuration.md)
- [Events](docs/events.md)
- [Raw API](docs/raw-api.md)
- [Example](docs/examples/basic/main.go): lists the groups of a network, run it with `go run ./docs/examples/basic`
