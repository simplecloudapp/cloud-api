package simplecloud

import (
	"testing"

	"google.golang.org/protobuf/proto"
)

func TestEventTypesUseControllerProtobuf(t *testing.T) {
	original := &ServerGroupCreatedEvent{NetworkId: "network-1", ServerGroupId: "group-1", Timestamp: 42}
	payload, err := proto.Marshal(original)
	if err != nil {
		t.Fatal(err)
	}
	decoded := &ServerGroupCreatedEvent{}
	if err := proto.Unmarshal(payload, decoded); err != nil {
		t.Fatal(err)
	}
	if !proto.Equal(original, decoded) {
		t.Fatalf("decoded event = %v", decoded)
	}
}
