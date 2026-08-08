package main

import (
	"context"
	"fmt"
	"log"
	"time"

	simplecloud "github.com/simplecloudapp/cloud-api/go"
)

func main() {
	if err := run(); err != nil {
		log.Fatal(err)
	}
}

func run() error {
	client, err := simplecloud.NewClientFromEnv()
	if err != nil {
		return fmt.Errorf("create SimpleCloud client: %w", err)
	}
	defer func() { _ = client.Close() }()

	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	groups, response, err := client.Groups.List(ctx, nil)
	if err != nil {
		if response != nil {
			return fmt.Errorf("list groups (%s): %w", response.Status, err)
		}
		return fmt.Errorf("list groups: %w", err)
	}

	fmt.Printf("network %q has %d group(s)\n", client.NetworkID(), len(groups))
	for _, group := range groups {
		fmt.Printf("- %s (%s)\n", group.GetName(), group.GetServerGroupId())
	}

	return nil
}
