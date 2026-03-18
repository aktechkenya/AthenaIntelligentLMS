package handler

import (
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestResolveTenantID_FromHeader(t *testing.T) {
	r := httptest.NewRequest(http.MethodGet, "/", nil)
	r.Header.Set("X-Tenant-Id", "tenant-abc")

	tid := resolveTenantID(r)
	assert.Equal(t, "tenant-abc", tid)
}

func TestResolveTenantID_Default(t *testing.T) {
	r := httptest.NewRequest(http.MethodGet, "/", nil)

	tid := resolveTenantID(r)
	assert.Equal(t, "default", tid)
}

func TestParsePagination_Defaults(t *testing.T) {
	r := httptest.NewRequest(http.MethodGet, "/", nil)
	page, size := parsePagination(r)
	assert.Equal(t, 0, page)
	assert.Equal(t, 20, size)
}

func TestParsePagination_Custom(t *testing.T) {
	r := httptest.NewRequest(http.MethodGet, "/?page=2&size=50", nil)
	page, size := parsePagination(r)
	assert.Equal(t, 2, page)
	assert.Equal(t, 50, size)
}

func TestParsePagination_InvalidValues(t *testing.T) {
	r := httptest.NewRequest(http.MethodGet, "/?page=-1&size=abc", nil)
	page, size := parsePagination(r)
	assert.Equal(t, 0, page)  // negative falls back to default
	assert.Equal(t, 20, size) // non-numeric falls back to default
}

func TestParsePagination_SizeExceedsMax(t *testing.T) {
	r := httptest.NewRequest(http.MethodGet, "/?size=200", nil)
	_, size := parsePagination(r)
	assert.Equal(t, 20, size) // exceeds max of 100, falls back to default
}
