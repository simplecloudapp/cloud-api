package simplecloud

import "github.com/nats-io/nats.go"

func (c *EventsClient) OnPlayerLogin(handler func(*PlayerLoginEvent)) (*nats.Subscription, error) {
	return subscribeEvent(c, "event.player.login", func() *PlayerLoginEvent { return &PlayerLoginEvent{} }, handler)
}

func (c *EventsClient) OnPlayerDisconnect(handler func(*PlayerDisconnectEvent)) (*nats.Subscription, error) {
	return subscribeEvent(c, "event.player.disconnect", func() *PlayerDisconnectEvent { return &PlayerDisconnectEvent{} }, handler)
}

func (c *EventsClient) OnPlayerServerSwitch(handler func(*PlayerServerSwitchEvent)) (*nats.Subscription, error) {
	return subscribeEvent(c, "event.player.server-switch", func() *PlayerServerSwitchEvent {
		return &PlayerServerSwitchEvent{}
	}, handler)
}

func (c *EventsClient) OnPlayerKick(handler func(*PlayerKickEvent)) (*nats.Subscription, error) {
	return subscribeEvent(c, "event.player.kick", func() *PlayerKickEvent { return &PlayerKickEvent{} }, handler)
}

func (c *EventsClient) OnPlayerProfileChanged(handler func(*PlayerProfileChangedEvent)) (*nats.Subscription, error) {
	return subscribeEvent(c, "event.player.profile-changed", func() *PlayerProfileChangedEvent {
		return &PlayerProfileChangedEvent{}
	}, handler)
}

func (c *EventsClient) OnPlayerClientSettingsUpdated(handler func(*PlayerClientSettingsUpdatedEvent)) (*nats.Subscription, error) {
	return subscribeEvent(c, "event.player.client-settings-updated", func() *PlayerClientSettingsUpdatedEvent {
		return &PlayerClientSettingsUpdatedEvent{}
	}, handler)
}
