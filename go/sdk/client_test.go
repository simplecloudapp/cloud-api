package simplecloud

import "testing"

func TestNewClientRejectsInvalidControllerURL(t *testing.T) {
	if _, err := NewClient(Options{ControllerURL: "not-a-url"}); err == nil {
		t.Fatal("expected invalid URL error")
	}
}
