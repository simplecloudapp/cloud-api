# SimpleCloud Go API

The standalone Go client for the SimpleCloud Controller. It includes:

- an idiomatic facade for groups, servers, persistent servers, players, and events;
- typed NATS subscriptions using the same protobuf messages and subjects as the Java API;
- the complete generated REST surface for every controller endpoint, available through `Client.Raw`;
- all OpenAPI request and response models in the `generated` subpackage.

This module is a Go library. It is not a Minecraft plugin and has no dependency on a Minecraft server runtime.

## Install

```sh
go get github.com/simplecloudapp/cloud-api/go
```

The module requires Go 1.23 or newer.

## Usage

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

`NewClientFromEnv` reads:

- `SIMPLECLOUD_NETWORK_ID` (default `default`)
- `SIMPLECLOUD_NETWORK_SECRET`
- `SIMPLECLOUD_CONTROLLER_URL` (default `https://controller.simplecloud.app`)
- `SIMPLECLOUD_NATS_URL` (default `wss://nats.simplecloud.app:443`)
- `SIMPLECLOUD_COMPONENT` (optional `X-SC-Component` header)

You can also pass `Options` directly to `NewClient`, including a custom `http.Client` or additional NATS options.

## Example project

A small runnable command is available in [`examples/basic`](examples/basic). It loads the standard environment variables, lists the network's groups, and prints their names and IDs:

```sh
export SIMPLECLOUD_NETWORK_ID="your-network-id"
export SIMPLECLOUD_NETWORK_SECRET="your-network-secret"
go run ./examples/basic
```

## Events

NATS is connected lazily on the first subscription. The returned `*nats.Subscription` can be drained or unsubscribed normally.

```go
subscription, err := client.Events.OnServerStarted(func(event *simplecloud.ServerStartedEvent) {
	fmt.Printf("server %s started\n", event.GetServerId())
})
if err != nil {
	log.Fatal(err)
}
defer subscription.Unsubscribe()
```

Group, server, persistent-server, and blueprint events all have typed subscription methods.

## Complete controller API

The facade intentionally keeps common operations compact. Every controller endpoint—including networks, blueprints, metrics, plugins, secrets, serverhosts, stats, workflows, rolling restarts, and player sessions—is exposed through the generated `Raw` client:

```go
result, response, err := client.Raw.StatsAPI.V0StatsGet(context.Background()).
	XNetworkID(client.NetworkID()).
	XNetworkCredential("network-secret").
	Execute()
```

Generated request builders require the network headers explicitly. The facade (`Groups`, `Servers`, `PersistentServers`, and `Players`) supplies them automatically.

Code using models outside the facade can import the generated package directly:

```go
import "github.com/simplecloudapp/cloud-api/go/generated"
```

## Regeneration

The checked-in client is generated from `api/openapi.yaml`. From the repository root:

```sh
./gradlew :api:generateGoClient
cd go
go fmt ./...
go mod tidy
go test ./...
```

The generator writes only to `go/generated`; the facade, examples, module metadata, and this README remain separate.
