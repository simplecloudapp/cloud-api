package simplecloud

import (
	"os"
	"strings"
	"testing"
	"time"

	"github.com/nats-io/nats.go"
)

// Run against an isolated broker with NATS_TEST_URL=nats://127.0.0.1:4222.
func TestNetworkScopedRequestInboxes(t *testing.T) {
	url := os.Getenv("NATS_TEST_URL")
	if url == "" {
		t.Skip("set NATS_TEST_URL to run broker integration tests")
	}
	responder, err := nats.Connect(url)
	if err != nil {
		t.Fatal(err)
	}
	defer responder.Close()
	subject := "network-1.test." + nats.NewInbox()
	_, err = responder.Subscribe(subject, func(msg *nats.Msg) {
		_ = msg.Respond([]byte(msg.Reply))
	})
	if err != nil {
		t.Fatal(err)
	}
	if err := responder.Flush(); err != nil {
		t.Fatal(err)
	}

	for _, oldStyle := range []bool{false, true} {
		name := "multiplexed"
		if oldStyle {
			name = "old-style"
		}
		t.Run(name, func(t *testing.T) {
			options := []nats.Option{nats.CustomInboxPrefix("_INBOX"), nats.Name(name)}
			if oldStyle {
				options = append(options, nats.UseOldRequestStyle())
			}
			client, err := NewClient(Options{
				NetworkID: "network-1", NetworkSecret: "secret", NATSURL: url, NATSOptions: options,
			})
			if err != nil {
				t.Fatal(err)
			}
			t.Cleanup(func() { _ = client.Close() })
			connection, err := client.NATS()
			if err != nil {
				t.Fatal(err)
			}
			assertScoped := func(connection *nats.Conn) {
				t.Helper()
				if connection.Opts.Name != name {
					t.Fatal("custom connection options were not preserved")
				}
				if inbox := connection.NewInbox(); !strings.HasPrefix(inbox, "network-1._INBOX.") {
					t.Fatalf("unexpected generated inbox %q", inbox)
				}
				response, err := connection.Request(subject, nil, 5*time.Second)
				if err != nil {
					t.Fatal(err)
				}
				if !strings.HasPrefix(string(response.Data), "network-1._INBOX.") {
					t.Fatalf("unexpected request reply subject %q", response.Data)
				}
			}
			assertScoped(connection)
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
			assertScoped(connection)
			connection.Close()
			replacement, err := client.NATS()
			if err != nil {
				t.Fatal(err)
			}
			if replacement == connection {
				t.Fatal("closed connection was reused")
			}
			assertScoped(replacement)
		})
	}
}
