package simplecloud

import (
	"strings"
	"testing"
	"time"

	"github.com/nats-io/nats.go"
)

func newNATSTestClient(t *testing.T, url string, natsOptions []nats.Option) *Client {
	t.Helper()
	client, err := NewClient(Options{
		NetworkID:     testNetworkID,
		NetworkSecret: "secret",
		NATSURL:       url,
		NATSOptions:   natsOptions,
	})
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { _ = client.Close() })
	return client
}

func connectNATS(t *testing.T, client *Client) *nats.Conn {
	t.Helper()
	connection, err := client.NATS()
	if err != nil {
		t.Fatal(err)
	}
	return connection
}

func forceReconnect(t *testing.T, connection *nats.Conn) {
	t.Helper()
	if err := connection.ForceReconnect(); err != nil {
		t.Fatal(err)
	}
	deadline := time.Now().Add(10 * time.Second)
	for !connection.IsConnected() && time.Now().Before(deadline) {
		time.Sleep(10 * time.Millisecond)
	}
	if !connection.IsConnected() {
		t.Fatal("connection did not reconnect")
	}
}

func assertRequestsUseNetworkInbox(t *testing.T, connection *nats.Conn, subject string, connectionName string) {
	t.Helper()
	networkInboxPrefix := testNetworkID + "._INBOX."

	if connection.Opts.Name != connectionName {
		t.Fatal("custom connection options were not preserved")
	}
	inbox := connection.NewInbox()
	if !strings.HasPrefix(inbox, networkInboxPrefix) {
		t.Fatalf("unexpected generated inbox %q", inbox)
	}

	response, err := connection.Request(subject, nil, 5*time.Second)
	if err != nil {
		t.Fatal(err)
	}
	replySubject := string(response.Data)
	if !strings.HasPrefix(replySubject, networkInboxPrefix) {
		t.Fatalf("unexpected request reply subject %q", replySubject)
	}
}
