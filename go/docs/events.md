# Events

Every event has a typed subscription method on `client.Events`. Each returns a `*nats.Subscription` that you can unsubscribe or drain.

```go
subscription, err := client.Events.OnServerStarted(func(event *simplecloud.ServerStartedEvent) {
	fmt.Printf("server %s started\n", event.GetServerId())
})
if err != nil {
	log.Fatal(err)
}
defer subscription.Unsubscribe()
```

| Type | Events |
|---|---|
| Groups | created, updated, deleted |
| Servers | started, stopped, state changed, updated, deleted |
| Persistent servers | created, started, stopped, updated, deleted |
| Blueprints | created, updated, deleted |
| Players | login, disconnect, server switch, kick, profile changed, client settings updated |

## Connecting a player over NATS

`ConnectDirect` asks the player's proxy to move the player to a server. If the context has no deadline, it times out after 30 seconds.

```go
result, err := client.Players.ConnectDirect(ctx, playerID, "lobby-1")
```

`ConnectToGroup` and `ConnectToPersistentServer` do the same through the controller.
