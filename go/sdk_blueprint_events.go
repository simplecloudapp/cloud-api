package simplecloud

import "github.com/nats-io/nats.go"

func (c *EventsClient) OnBlueprintCreated(handler func(*BlueprintCreatedEvent)) (*nats.Subscription, error) {
	return subscribeEvent(c, "event.blueprint.created", func() *BlueprintCreatedEvent { return &BlueprintCreatedEvent{} }, handler)
}

func (c *EventsClient) OnBlueprintUpdated(handler func(*BlueprintUpdatedEvent)) (*nats.Subscription, error) {
	return subscribeEvent(c, "event.blueprint.updated", func() *BlueprintUpdatedEvent { return &BlueprintUpdatedEvent{} }, handler)
}

func (c *EventsClient) OnBlueprintDeleted(handler func(*BlueprintDeletedEvent)) (*nats.Subscription, error) {
	return subscribeEvent(c, "event.blueprint.deleted", func() *BlueprintDeletedEvent { return &BlueprintDeletedEvent{} }, handler)
}
