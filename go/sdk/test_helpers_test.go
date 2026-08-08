package simplecloud

import "testing"

func newTestClient(t *testing.T, controllerURL string) *Client {
	t.Helper()
	client, err := NewClient(Options{
		ControllerURL: controllerURL,
		NetworkID:     "network-1",
		NetworkSecret: "secret-1",
		Component:     "tests",
	})
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { _ = client.Close() })
	return client
}
