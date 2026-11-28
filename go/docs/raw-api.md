# Raw API

`client.Raw` is the generated client for every controller endpoint. Use it for anything the clients above do not cover, such as serverhosts, plugins, secrets, environment variables, metrics, logs and audit events.

```go
hosts, _, err := client.Raw.ServerhostsAPI.ListServerhosts(ctx).
	XNetworkID(client.NetworkID()).
	Execute()
```

The client adds the network secret to every request. Raw requests still need `XNetworkID`.

The request and response models live in the `generated` package:

```go
import "github.com/simplecloudapp/cloud-api/go/generated"
```
