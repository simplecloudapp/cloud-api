package simplecloud

func toWireProperties(properties map[string]any) map[string]*any {
	if properties == nil {
		return nil
	}
	result := make(map[string]*any, len(properties))
	for key, value := range properties {
		result[key] = &value
	}
	return result
}

func fromWireProperties(properties map[string]*any) map[string]any {
	if properties == nil {
		return nil
	}
	result := make(map[string]any, len(properties))
	for key, value := range properties {
		var plain any
		if value != nil {
			plain = *value
		}
		result[key] = plain
	}
	return result
}
