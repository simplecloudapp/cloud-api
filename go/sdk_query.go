package simplecloud

import (
	"strconv"
	"strings"
)

func stringQuery(values []string) string {
	return strings.Join(values, ",")
}

func int32Query(values []int32) string {
	parts := make([]string, len(values))
	for index, value := range values {
		parts[index] = strconv.FormatInt(int64(value), 10)
	}
	return strings.Join(parts, ",")
}
