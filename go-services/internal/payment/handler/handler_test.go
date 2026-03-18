package handler

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/go-chi/chi/v5"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"go.uber.org/zap"

	commonerr "github.com/athena-lms/go-services/internal/common/errors"
	"github.com/athena-lms/go-services/internal/common/httputil"
)

func TestWriteError_NotFound(t *testing.T) {
	h := New(nil, zap.NewNop())
	w := httptest.NewRecorder()
	r := httptest.NewRequest("GET", "/api/v1/payments/123", nil)

	h.writeError(w, r, commonerr.NotFoundResource("Payment", "123"))

	assert.Equal(t, http.StatusNotFound, w.Code)

	var resp httputil.ErrorResponse
	require.NoError(t, json.NewDecoder(w.Body).Decode(&resp))
	assert.Equal(t, 404, resp.Status)
	assert.Contains(t, resp.Message, "Payment not found")
}

func TestWriteError_BusinessError(t *testing.T) {
	h := New(nil, zap.NewNop())
	w := httptest.NewRecorder()
	r := httptest.NewRequest("POST", "/api/v1/payments/123/complete", nil)

	h.writeError(w, r, commonerr.NewBusinessError("Payment must be PENDING or PROCESSING"))

	assert.Equal(t, http.StatusUnprocessableEntity, w.Code)

	var resp httputil.ErrorResponse
	require.NoError(t, json.NewDecoder(w.Body).Decode(&resp))
	assert.Contains(t, resp.Message, "PENDING or PROCESSING")
}

func TestWriteError_BadRequest(t *testing.T) {
	h := New(nil, zap.NewNop())
	w := httptest.NewRecorder()
	r := httptest.NewRequest("POST", "/api/v1/payments", nil)

	h.writeError(w, r, commonerr.BadRequest("customerId is required"))

	assert.Equal(t, http.StatusBadRequest, w.Code)

	var resp httputil.ErrorResponse
	require.NoError(t, json.NewDecoder(w.Body).Decode(&resp))
	assert.Contains(t, resp.Message, "customerId is required")
}

func TestRegisterRoutes(t *testing.T) {
	h := New(nil, zap.NewNop())
	r := chi.NewRouter()
	h.RegisterRoutes(r)

	// Walk the routes to verify all 11 endpoints are registered
	routes := make(map[string]bool)
	chi.Walk(r, func(method, route string, handler http.Handler, middlewares ...func(http.Handler) http.Handler) error {
		routes[method+" "+route] = true
		return nil
	})

	assert.True(t, routes["POST /api/v1/payments/"], "POST /api/v1/payments/")
	assert.True(t, routes["GET /api/v1/payments/"], "GET /api/v1/payments/")
	assert.True(t, routes["GET /api/v1/payments/{id}"], "GET /api/v1/payments/{id}")
	assert.True(t, routes["GET /api/v1/payments/customer/{customerId}"], "GET /api/v1/payments/customer/{customerId}")
	assert.True(t, routes["GET /api/v1/payments/reference/{ref}"], "GET /api/v1/payments/reference/{ref}")
	assert.True(t, routes["POST /api/v1/payments/{id}/process"], "POST /api/v1/payments/{id}/process")
	assert.True(t, routes["POST /api/v1/payments/{id}/complete"], "POST /api/v1/payments/{id}/complete")
	assert.True(t, routes["POST /api/v1/payments/{id}/fail"], "POST /api/v1/payments/{id}/fail")
	assert.True(t, routes["POST /api/v1/payments/{id}/reverse"], "POST /api/v1/payments/{id}/reverse")
	assert.True(t, routes["POST /api/v1/payments/methods"], "POST /api/v1/payments/methods")
	assert.True(t, routes["GET /api/v1/payments/methods/customer/{customerId}"], "GET /api/v1/payments/methods/customer/{customerId}")
}

func TestInitiate_InvalidJSON(t *testing.T) {
	h := New(nil, zap.NewNop())
	r := chi.NewRouter()
	h.RegisterRoutes(r)

	req := httptest.NewRequest("POST", "/api/v1/payments/", strings.NewReader("{invalid"))
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	assert.Equal(t, http.StatusBadRequest, w.Code)
}

func TestGetByID_InvalidUUID(t *testing.T) {
	h := New(nil, zap.NewNop())
	r := chi.NewRouter()
	h.RegisterRoutes(r)

	req := httptest.NewRequest("GET", "/api/v1/payments/not-a-uuid", nil)
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	assert.Equal(t, http.StatusBadRequest, w.Code)
}

func TestFail_InvalidJSON(t *testing.T) {
	h := New(nil, zap.NewNop())
	r := chi.NewRouter()
	h.RegisterRoutes(r)

	req := httptest.NewRequest("POST", "/api/v1/payments/00000000-0000-0000-0000-000000000001/fail",
		strings.NewReader("{bad json"))
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	assert.Equal(t, http.StatusBadRequest, w.Code)
}

func TestReverse_InvalidJSON(t *testing.T) {
	h := New(nil, zap.NewNop())
	r := chi.NewRouter()
	h.RegisterRoutes(r)

	req := httptest.NewRequest("POST", "/api/v1/payments/00000000-0000-0000-0000-000000000001/reverse",
		strings.NewReader("{bad json"))
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	assert.Equal(t, http.StatusBadRequest, w.Code)
}

func TestAddMethod_InvalidJSON(t *testing.T) {
	h := New(nil, zap.NewNop())
	r := chi.NewRouter()
	h.RegisterRoutes(r)

	req := httptest.NewRequest("POST", "/api/v1/payments/methods", strings.NewReader("{bad"))
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	assert.Equal(t, http.StatusBadRequest, w.Code)
}
