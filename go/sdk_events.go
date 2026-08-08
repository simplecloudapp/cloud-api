package simplecloud

import "sync"

// EventErrorHandler receives asynchronous protobuf decoding errors. Malformed
// messages are ignored when no handler is installed.
type EventErrorHandler func(subject string, err error)

// EventsClient subscribes to the same NATS subjects as the Java API.
type EventsClient struct {
	client *Client

	mu      sync.RWMutex
	onError EventErrorHandler
}

func (c *EventsClient) SetErrorHandler(handler EventErrorHandler) {
	c.mu.Lock()
	c.onError = handler
	c.mu.Unlock()
}

func (c *EventsClient) reportError(subject string, err error) {
	c.mu.RLock()
	handler := c.onError
	c.mu.RUnlock()
	if handler != nil {
		handler(subject, err)
	}
}
