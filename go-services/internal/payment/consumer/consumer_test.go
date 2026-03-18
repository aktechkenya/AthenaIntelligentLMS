package consumer

import (
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestGetStr(t *testing.T) {
	m := map[string]any{
		"name":   "John",
		"age":    30,
		"nil":    nil,
		"nested": map[string]any{"key": "val"},
	}

	assert.Equal(t, "John", getStr(m, "name"))
	assert.Equal(t, "30", getStr(m, "age"))
	assert.Equal(t, "", getStr(m, "nil"))
	assert.Equal(t, "", getStr(m, "missing"))
	assert.NotEmpty(t, getStr(m, "nested")) // should format as map string
}

func TestGetStr_EmptyMap(t *testing.T) {
	m := map[string]any{}
	assert.Equal(t, "", getStr(m, "anything"))
}
