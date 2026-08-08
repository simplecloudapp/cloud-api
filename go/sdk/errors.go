package simplecloud

import "errors"

// ErrNotFound indicates that a facade lookup returned no matching resource.
var ErrNotFound = errors.New("simplecloud: resource not found")
