package simplecloud

import "github.com/nats-io/nats.go"

func (c *EventsClient) OnGroupCreated(handler func(*ServerGroupCreatedEvent)) (*nats.Subscription, error) {
	return subscribeEvent(c, "event.group.created", func() *ServerGroupCreatedEvent {
		return &ServerGroupCreatedEvent{}
	}, handler)
}

func (c *EventsClient) OnGroupUpdated(handler func(*ServerGroupUpdatedEvent)) (*nats.Subscription, error) {
	return subscribeEvent(c, "event.group.updated", func() *ServerGroupUpdatedEvent {
		return &ServerGroupUpdatedEvent{}
	}, handler)
}

func (c *EventsClient) OnGroupDeleted(handler func(*ServerGroupDeletedEvent)) (*nats.Subscription, error) {
	return subscribeEvent(c, "event.group.deleted", func() *ServerGroupDeletedEvent {
		return &ServerGroupDeletedEvent{}
	}, handler)
}
