package simplecloud

import (
	"os"
	"testing"

	"github.com/nats-io/nats.go"
)

const testNetworkID = "network-1"

// Run against an isolated broker with NATS_TEST_URL=nats://127.0.0.1:4222.
func TestNetworkScopedRequestInboxes(t *testing.T) {
	url := os.Getenv("NATS_TEST_URL")
	if url == "" {
		t.Skip("set NATS_TEST_URL to run broker integration tests")
	}
	subject := startReplySubjectEchoResponder(t, url)

	requestStyles := []struct {
		name    string
		options []nats.Option
	}{
		{name: "multiplexed"},
		{name: "old-style", options: []nats.Option{nats.UseOldRequestStyle()}},
	}
	for _, style := range requestStyles {
		t.Run(style.name, func(t *testing.T) {
			// The global prefix proves the SDK overrides a caller-supplied inbox prefix.
			callerOptions := append([]nats.Option{nats.CustomInboxPrefix("_INBOX"), nats.Name(style.name)}, style.options...)
			client := newNATSTestClient(t, url, callerOptions)

			connection := connectNATS(t, client)
			assertRequestsUseNetworkInbox(t, connection, subject, style.name)

			forceReconnect(t, connection)
			assertRequestsUseNetworkInbox(t, connection, subject, style.name)

			connection.Close()
			replacement := connectNATS(t, client)
			if replacement == connection {
				t.Fatal("closed connection was reused")
			}
			assertRequestsUseNetworkInbox(t, replacement, subject, style.name)
		})
	}
}

// startReplySubjectEchoResponder answers every request with the request's own reply subject.
func startReplySubjectEchoResponder(t *testing.T, url string) string {
	t.Helper()
	responder, err := nats.Connect(url)
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(responder.Close)

	subject := testNetworkID + ".test." + nats.NewInbox()
	_, err = responder.Subscribe(subject, func(msg *nats.Msg) {
		_ = msg.Respond([]byte(msg.Reply))
	})
	if err != nil {
		t.Fatal(err)
	}
	if err := responder.Flush(); err != nil {
		t.Fatal(err)
	}
	return subject
}
