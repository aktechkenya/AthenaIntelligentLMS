package dto

import (
	"encoding/json"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestNewPageResponse(t *testing.T) {
	items := []string{"a", "b", "c"}
	resp := NewPageResponse(items, 0, 10, 25)

	assert.Equal(t, 0, resp.Page)
	assert.Equal(t, 10, resp.Size)
	assert.Equal(t, int64(25), resp.TotalElements)
	assert.Equal(t, 3, resp.TotalPages)
	assert.False(t, resp.Last)
}

func TestNewPageResponse_LastPage(t *testing.T) {
	items := []string{"x"}
	resp := NewPageResponse(items, 2, 10, 25)

	assert.Equal(t, 2, resp.Page)
	assert.True(t, resp.Last)
}

func TestPageResponseJSONShape(t *testing.T) {
	resp := NewPageResponse([]int{1, 2}, 0, 10, 2)

	b, err := json.Marshal(resp)
	require.NoError(t, err)

	var m map[string]any
	err = json.Unmarshal(b, &m)
	require.NoError(t, err)

	// Verify camelCase field names match Java PageResponse exactly
	assert.Contains(t, m, "content")
	assert.Contains(t, m, "page")
	assert.Contains(t, m, "size")
	assert.Contains(t, m, "totalElements")
	assert.Contains(t, m, "totalPages")
	assert.Contains(t, m, "last")
}
