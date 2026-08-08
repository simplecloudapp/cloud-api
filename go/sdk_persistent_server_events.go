package simplecloud

import "github.com/nats-io/nats.go"

func (c *EventsClient) OnPersistentServerCreated(handler func(*PersistentServerCreatedEvent)) (*nats.Subscription, error) {
	return subscribeEvent(c, "event.persistent-server.created", func() *PersistentServerCreatedEvent {
		return &PersistentServerCreatedEvent{}
	}, handler)
}

func (c *EventsClient) OnPersistentServerStarted(handler func(*PersistentServerStartedEvent)) (*nats.Subscription, error) {
	return subscribeEvent(c, "event.persistent-server.started", func() *PersistentServerStartedEvent {
		return &PersistentServerStartedEvent{}
	}, handler)
}

func (c *EventsClient) OnPersistentServerStopped(handler func(*PersistentServerStoppedEvent)) (*nats.Subscription, error) {
	return subscribeEvent(c, "event.persistent-server.stopped", func() *PersistentServerStoppedEvent {
		return &PersistentServerStoppedEvent{}
	}, handler)
}

func (c *EventsClient) OnPersistentServerUpdated(handler func(*PersistentServerUpdatedEvent)) (*nats.Subscription, error) {
	return subscribeEvent(c, "event.persistent-server.updated", func() *PersistentServerUpdatedEvent {
		return &PersistentServerUpdatedEvent{}
	}, handler)
}

func (c *EventsClient) OnPersistentServerDeleted(handler func(*PersistentServerDeletedEvent)) (*nats.Subscription, error) {
	return subscribeEvent(c, "event.persistent-server.deleted", func() *PersistentServerDeletedEvent {
		return &PersistentServerDeletedEvent{}
	}, handler)
}
