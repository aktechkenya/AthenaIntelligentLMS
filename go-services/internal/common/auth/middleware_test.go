package auth

import (
	"encoding/base64"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"go.uber.org/zap"
)

func TestMiddleware_BearerAuth(t *testing.T) {
	secret := base64.StdEncoding.EncodeToString([]byte("01234567890123456789012345678901"))
	jwtUtil, err := NewJWTUtil(secret)
	require.NoError(t, err)

	logger := zap.NewNop()
	mw := NewMiddleware(jwtUtil, "test-key", logger)

	token := createTestToken(t, secret, map[string]any{
		"sub":      "admin",
		"tenantId": "tenant1",
		"roles":    []string{"ADMIN"},
	})

	var capturedTenant, capturedUser string
	handler := mw.Handler(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		capturedTenant = TenantIDFromContext(r.Context())
		capturedUser = UserIDFromContext(r.Context())
		w.WriteHeader(http.StatusOK)
	}))

	req := httptest.NewRequest("GET", "/api/test", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	rec := httptest.NewRecorder()

	handler.ServeHTTP(rec, req)

	assert.Equal(t, http.StatusOK, rec.Code)
	assert.Equal(t, "tenant1", capturedTenant)
	assert.Equal(t, "admin", capturedUser)
}

func TestMiddleware_ServiceKeyAuth(t *testing.T) {
	secret := base64.StdEncoding.EncodeToString([]byte("01234567890123456789012345678901"))
	jwtUtil, err := NewJWTUtil(secret)
	require.NoError(t, err)

	logger := zap.NewNop()
	mw := NewMiddleware(jwtUtil, "test-service-key", logger)

	var capturedTenant, capturedUser string
	var capturedRoles []string
	handler := mw.Handler(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		capturedTenant = TenantIDFromContext(r.Context())
		capturedUser = UserIDFromContext(r.Context())
		capturedRoles = RolesFromContext(r.Context())
		w.WriteHeader(http.StatusOK)
	}))

	req := httptest.NewRequest("GET", "/api/test", nil)
	req.Header.Set("X-Service-Key", "test-service-key")
	req.Header.Set("X-Service-Tenant", "tenant2")
	req.Header.Set("X-Service-User", "payment-service")
	rec := httptest.NewRecorder()

	handler.ServeHTTP(rec, req)

	assert.Equal(t, http.StatusOK, rec.Code)
	assert.Equal(t, "tenant2", capturedTenant)
	assert.Equal(t, "payment-service", capturedUser)
	assert.Equal(t, []string{"SERVICE", "ADMIN"}, capturedRoles)
}

func TestMiddleware_NoCredentials(t *testing.T) {
	secret := base64.StdEncoding.EncodeToString([]byte("01234567890123456789012345678901"))
	jwtUtil, err := NewJWTUtil(secret)
	require.NoError(t, err)

	logger := zap.NewNop()
	mw := NewMiddleware(jwtUtil, "test-key", logger)

	handler := mw.Handler(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		t.Fatal("should not reach handler")
	}))

	req := httptest.NewRequest("GET", "/api/test", nil)
	rec := httptest.NewRecorder()

	handler.ServeHTTP(rec, req)

	assert.Equal(t, http.StatusUnauthorized, rec.Code)

	var body map[string]any
	err = json.NewDecoder(rec.Body).Decode(&body)
	require.NoError(t, err)
	assert.Equal(t, float64(401), body["status"])
	assert.Equal(t, "Unauthorized", body["error"])
}

func TestMiddleware_HealthEndpointSkipsAuth(t *testing.T) {
	secret := base64.StdEncoding.EncodeToString([]byte("01234567890123456789012345678901"))
	jwtUtil, err := NewJWTUtil(secret)
	require.NoError(t, err)

	logger := zap.NewNop()
	mw := NewMiddleware(jwtUtil, "test-key", logger)

	reached := false
	handler := mw.Handler(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		reached = true
		w.WriteHeader(http.StatusOK)
	}))

	req := httptest.NewRequest("GET", "/actuator/health", nil)
	rec := httptest.NewRecorder()

	handler.ServeHTTP(rec, req)

	assert.True(t, reached)
	assert.Equal(t, http.StatusOK, rec.Code)
}

func TestMiddleware_ServiceKeyDefaultTenant(t *testing.T) {
	secret := base64.StdEncoding.EncodeToString([]byte("01234567890123456789012345678901"))
	jwtUtil, err := NewJWTUtil(secret)
	require.NoError(t, err)

	logger := zap.NewNop()
	mw := NewMiddleware(jwtUtil, "key", logger)

	var capturedTenant, capturedUser string
	handler := mw.Handler(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		capturedTenant = TenantIDFromContext(r.Context())
		capturedUser = UserIDFromContext(r.Context())
		w.WriteHeader(http.StatusOK)
	}))

	// No X-Service-Tenant or X-Service-User headers
	req := httptest.NewRequest("GET", "/api/test", nil)
	req.Header.Set("X-Service-Key", "key")
	rec := httptest.NewRecorder()

	handler.ServeHTTP(rec, req)

	assert.Equal(t, http.StatusOK, rec.Code)
	assert.Equal(t, "default", capturedTenant)
	assert.Equal(t, "internal-service", capturedUser)
}
