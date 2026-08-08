package simplecloud

import (
	"fmt"

	"github.com/nats-io/nats.go"
	"google.golang.org/protobuf/proto"
)

func subscribeEvent[T proto.Message](events *EventsClient, suffix string, factory func() T, handler func(T)) (*nats.Subscription, error) {
	if handler == nil {
		return nil, fmt.Errorf("simplecloud: event handler for %s must not be nil", suffix)
	}
	connection, err := events.client.NATS()
	if err != nil {
		return nil, err
	}
	subject := events.client.NetworkID() + "." + suffix
	return connection.Subscribe(subject, func(message *nats.Msg) {
		event := factory()
		if err := proto.Unmarshal(message.Data, event); err != nil {
			events.reportError(subject, fmt.Errorf("decode event: %w", err))
			return
		}
		handler(event)
	})
}
