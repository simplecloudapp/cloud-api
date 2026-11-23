# Configuration

`NewClientFromEnv` reads these environment variables:

| Variable | Default |
|---|---|
| `SIMPLECLOUD_NETWORK_ID` | `default` |
| `SIMPLECLOUD_NETWORK_SECRET` | empty |
| `SIMPLECLOUD_CONTROLLER_URL` | `https://controller.simplecloud.app` |
| `SIMPLECLOUD_NATS_URL` | `wss://nats.simplecloud.app:443` |
| `SIMPLECLOUD_COMPONENT` | empty, sent as `X-SC-Component` when set |

To configure the client in code, pass `Options` to `NewClient`. Empty fields fall back to the variables above.

```go
client, err := simplecloud.NewClient(simplecloud.Options{
	NetworkID:     "my-network",
	NetworkSecret: os.Getenv("NETWORK_SECRET"),
	HTTPClient:    &http.Client{Timeout: 10 * time.Second},
	NATSOptions:   []nats.Option{nats.Name("my-service")},
})
```

Call `client.Close()` when you are done to close the NATS connection.
