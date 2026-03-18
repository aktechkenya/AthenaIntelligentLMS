package consumer

import (
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestGetStr(t *testing.T) {
	m := map[string]any{
		"name":   "John",
		"age":    25,
		"nil":    nil,
		"nested": map[string]any{"key": "val"},
	}

	assert.Equal(t, "John", getStr(m, "name"))
	assert.Equal(t, "25", getStr(m, "age"))
	assert.Equal(t, "", getStr(m, "nil"))
	assert.Equal(t, "", getStr(m, "missing"))
	assert.Equal(t, "map[key:val]", getStr(m, "nested"))
}

func TestGetAny(t *testing.T) {
	m := map[string]any{
		"amount":  1000.50,
		"nil_val": nil,
	}

	assert.Equal(t, 1000.50, getAny(m, "amount", "N/A"))
	assert.Equal(t, "N/A", getAny(m, "nil_val", "N/A"))
	assert.Equal(t, "N/A", getAny(m, "missing", "N/A"))
}
