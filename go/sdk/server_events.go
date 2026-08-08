package simplecloud

import "github.com/nats-io/nats.go"

func (c *EventsClient) OnServerStarted(handler func(*ServerStartedEvent)) (*nats.Subscription, error) {
	return subscribeEvent(c, "event.server.started", func() *ServerStartedEvent { return &ServerStartedEvent{} }, handler)
}

func (c *EventsClient) OnServerStopped(handler func(*ServerStoppedEvent)) (*nats.Subscription, error) {
	return subscribeEvent(c, "event.server.stopped", func() *ServerStoppedEvent { return &ServerStoppedEvent{} }, handler)
}

func (c *EventsClient) OnServerStateChanged(handler func(*ServerStateChangedEvent)) (*nats.Subscription, error) {
	return subscribeEvent(c, "event.server.state-changed", func() *ServerStateChangedEvent {
		return &ServerStateChangedEvent{}
	}, handler)
}

func (c *EventsClient) OnServerDeleted(handler func(*ServerDeletedEvent)) (*nats.Subscription, error) {
	return subscribeEvent(c, "event.server.deleted", func() *ServerDeletedEvent { return &ServerDeletedEvent{} }, handler)
}

func (c *EventsClient) OnServerUpdated(handler func(*ServerUpdatedEvent)) (*nats.Subscription, error) {
	return subscribeEvent(c, "event.server.updated", func() *ServerUpdatedEvent { return &ServerUpdatedEvent{} }, handler)
}
