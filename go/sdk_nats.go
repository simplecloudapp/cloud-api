package simplecloud

import (
	"fmt"
	"time"

	"github.com/nats-io/nats.go"
)

// NATS returns the lazily initialized NATS connection.
func (c *Client) NATS() (*nats.Conn, error) {
	c.natsMu.Lock()
	defer c.natsMu.Unlock()

	if c.nats != nil && !c.nats.IsClosed() {
		return c.nats, nil
	}

	options := []nats.Option{
		nats.UserInfo(c.options.NetworkID, c.options.NetworkSecret),
		nats.MaxReconnects(-1),
		nats.Timeout(5 * time.Second),
	}
	options = append(options, c.options.NATSOptions...)
	connection, err := nats.Connect(c.options.NATSURL, options...)
	if err != nil {
		return nil, fmt.Errorf("connect to SimpleCloud NATS: %w", err)
	}
	c.nats = connection
	return connection, nil
}

// Close drains and closes the optional NATS connection.
func (c *Client) Close() error {
	c.natsMu.Lock()
	defer c.natsMu.Unlock()
	if c.nats == nil || c.nats.IsClosed() {
		return nil
	}
	err := c.nats.Drain()
	c.nats.Close()
	c.nats = nil
	return err
}
