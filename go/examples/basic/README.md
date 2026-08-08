# Basic example

This small command uses the Go SDK to list every group in a SimpleCloud network.
It only calls the Controller REST API, so it does not need a Minecraft plugin or
a NATS connection.

From the `go` directory, set the network credentials and run it:

```sh
export SIMPLECLOUD_NETWORK_ID="your-network-id"
export SIMPLECLOUD_NETWORK_SECRET="your-network-secret"
go run ./examples/basic
```

Set `SIMPLECLOUD_CONTROLLER_URL` as well when using a self-hosted or local
Controller.
