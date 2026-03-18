package client

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/stretchr/testify/assert"
	"go.uber.org/zap"
)

func TestResolveEmail_Success(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		assert.Equal(t, "/api/v1/customers/by-customer-id/cust-123", r.URL.Path)
		assert.Equal(t, "test-key", r.Header.Get("X-Service-Key"))
		assert.Equal(t, "tenant-1", r.Header.Get("X-Service-Tenant"))
		assert.Equal(t, "notification-service", r.Header.Get("X-Service-User"))

		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(map[string]any{"email": "john@example.com"})
	}))
	defer server.Close()

	logger, _ := zap.NewDevelopment()
	c := NewCustomerClient(server.URL, "test-key", logger)

	email := c.ResolveEmail("cust-123", "tenant-1")
	assert.Equal(t, "john@example.com", email)
}

func TestResolveEmail_EmptyCustomerID(t *testing.T) {
	logger, _ := zap.NewDevelopment()
	c := NewCustomerClient("http://localhost:9999", "", logger)

	email := c.ResolveEmail("", "tenant-1")
	assert.Equal(t, "noreply@athena.lms", email)
}

func TestResolveEmail_ServerError(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusInternalServerError)
	}))
	defer server.Close()

	logger, _ := zap.NewDevelopment()
	c := NewCustomerClient(server.URL, "", logger)

	email := c.ResolveEmail("cust-123", "")
	assert.Equal(t, "noreply@athena.lms", email)
}

func TestResolveEmail_NoEmailInResponse(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(map[string]any{"name": "John Doe"})
	}))
	defer server.Close()

	logger, _ := zap.NewDevelopment()
	c := NewCustomerClient(server.URL, "", logger)

	email := c.ResolveEmail("cust-123", "")
	assert.Equal(t, "noreply@athena.lms", email)
}

func TestResolveEmail_ConnectionRefused(t *testing.T) {
	logger, _ := zap.NewDevelopment()
	c := NewCustomerClient("http://localhost:1", "", logger)

	email := c.ResolveEmail("cust-123", "tenant-1")
	assert.Equal(t, "noreply@athena.lms", email)
}

func TestResolveEmail_DefaultTenant(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		assert.Equal(t, "default", r.Header.Get("X-Service-Tenant"))
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(map[string]any{"email": "a@b.com"})
	}))
	defer server.Close()

	logger, _ := zap.NewDevelopment()
	c := NewCustomerClient(server.URL, "", logger)

	email := c.ResolveEmail("cust-1", "")
	assert.Equal(t, "a@b.com", email)
}
