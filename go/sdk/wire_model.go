package simplecloud

import (
	"encoding/json"
	"fmt"
	"net/http"
)

func mapWireResponse[T any](source any, response *http.Response, err error) (*T, *http.Response, error) {
	if err != nil || source == nil {
		return nil, response, err
	}
	result, err := mapWireModel[T](source)
	return result, response, err
}

func mapWireModel[T any](source any) (*T, error) {
	payload, err := json.Marshal(source)
	if err != nil {
		return nil, fmt.Errorf("simplecloud: map wire model: %w", err)
	}
	var result T
	if err := json.Unmarshal(payload, &result); err != nil {
		return nil, fmt.Errorf("simplecloud: map wire model: %w", err)
	}
	return &result, nil
}
