package simplecloud

import (
	"fmt"
	"net/http"
	"net/url"
	"os"
	"strings"

	"github.com/nats-io/nats.go"
)

const (
	DefaultControllerURL = "https://controller.simplecloud.app"
	DefaultNATSURL       = "wss://nats.simplecloud.app:443"
)

// Options configures a Client. Empty fields use the same environment variables
// and defaults as the Java Cloud API.
type Options struct {
	ControllerURL string
	NATSURL       string
	NetworkID     string
	NetworkSecret string
	Component     string
	HTTPClient    *http.Client
	NATSOptions   []nats.Option
}

// DefaultOptions returns configuration populated from the environment.
func DefaultOptions() Options {
	return Options{
		ControllerURL: environmentValue("SIMPLECLOUD_CONTROLLER_URL", DefaultControllerURL),
		NATSURL:       environmentValue("SIMPLECLOUD_NATS_URL", DefaultNATSURL),
		NetworkID:     environmentValue("SIMPLECLOUD_NETWORK_ID", "default"),
		NetworkSecret: os.Getenv("SIMPLECLOUD_NETWORK_SECRET"),
		Component:     os.Getenv("SIMPLECLOUD_COMPONENT"),
	}
}

func (options Options) withDefaults() Options {
	defaults := DefaultOptions()
	if options.ControllerURL == "" {
		options.ControllerURL = defaults.ControllerURL
	}
	if options.NATSURL == "" {
		options.NATSURL = defaults.NATSURL
	}
	if options.NetworkID == "" {
		options.NetworkID = defaults.NetworkID
	}
	if options.NetworkSecret == "" {
		options.NetworkSecret = defaults.NetworkSecret
	}
	if options.Component == "" {
		options.Component = defaults.Component
	}
	return options
}

func environmentValue(name, fallback string) string {
	if value := os.Getenv(name); value != "" {
		return value
	}
	return fallback
}

func parseControllerURL(value string) (string, error) {
	parsed, err := url.Parse(strings.TrimSpace(value))
	if err != nil || parsed.Scheme == "" || parsed.Host == "" {
		return "", fmt.Errorf("simplecloud: invalid controller URL %q", value)
	}
	return strings.TrimRight(parsed.String(), "/"), nil
}
