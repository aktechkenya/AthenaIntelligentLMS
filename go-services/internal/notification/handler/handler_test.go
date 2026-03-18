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

	"github.com/athena-lms/go-services/internal/notification/service"
)

// newTestRouter creates a handler with a nil service (tests that don't hit DB).
// For tests that exercise real logic, we'd need a mock repository.
func newTestRouter(h *Handler) *chi.Mux {
	r := chi.NewRouter()
	h.RegisterRoutes(r)
	return r
}

func TestSend_InvalidBody(t *testing.T) {
	logger, _ := zap.NewDevelopment()
	h := New(service.New(nil, logger), logger)
	r := newTestRouter(h)

	req := httptest.NewRequest(http.MethodPost, "/api/v1/notifications/send", strings.NewReader("{invalid"))
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	assert.Equal(t, http.StatusBadRequest, w.Code)

	var body map[string]any
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &body))
	assert.Equal(t, "Invalid request body", body["message"])
}

func TestSend_UnsupportedType(t *testing.T) {
	logger, _ := zap.NewDevelopment()
	h := New(service.New(nil, logger), logger)
	r := newTestRouter(h)

	payload := `{"type":"PUSH","recipient":"test@example.com","message":"Hello"}`
	req := httptest.NewRequest(http.MethodPost, "/api/v1/notifications/send", strings.NewReader(payload))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	assert.Equal(t, http.StatusBadRequest, w.Code)
}

func TestSend_SMSNotImplemented(t *testing.T) {
	logger, _ := zap.NewDevelopment()
	h := New(service.New(nil, logger), logger)
	r := newTestRouter(h)

	payload := `{"type":"SMS","recipient":"+254700000000","message":"Hello"}`
	req := httptest.NewRequest(http.MethodPost, "/api/v1/notifications/send", strings.NewReader(payload))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var body map[string]string
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &body))
	assert.Equal(t, "SMS not yet implemented", body["message"])
}

func TestUpdateConfig_InvalidBody(t *testing.T) {
	logger, _ := zap.NewDevelopment()
	h := New(service.New(nil, logger), logger)
	r := newTestRouter(h)

	req := httptest.NewRequest(http.MethodPost, "/api/v1/notifications/config", strings.NewReader("not json"))
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	assert.Equal(t, http.StatusBadRequest, w.Code)
}

func recoveryRouter(h *Handler) *chi.Mux {
	r := chi.NewRouter()
	r.Use(func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			defer func() {
				if rec := recover(); rec != nil {
					w.WriteHeader(http.StatusInternalServerError)
				}
			}()
			next.ServeHTTP(w, r)
		})
	})
	h.RegisterRoutes(r)
	return r
}

func TestGetConfig_RouteRegistered(t *testing.T) {
	logger, _ := zap.NewDevelopment()
	h := New(service.New(nil, logger), logger)
	r := recoveryRouter(h)

	req := httptest.NewRequest(http.MethodGet, "/api/v1/notifications/config/EMAIL", nil)
	w := httptest.NewRecorder()

	r.ServeHTTP(w, req)
	// Panics with nil repo -> recovered as 500, not 404 (route exists)
	assert.NotEqual(t, http.StatusNotFound, w.Code)
	assert.NotEqual(t, http.StatusMethodNotAllowed, w.Code)
}

func TestGetLogs_RouteRegistered(t *testing.T) {
	logger, _ := zap.NewDevelopment()
	h := New(service.New(nil, logger), logger)
	r := recoveryRouter(h)

	req := httptest.NewRequest(http.MethodGet, "/api/v1/notifications/logs?page=0&size=10", nil)
	w := httptest.NewRecorder()

	r.ServeHTTP(w, req)
	assert.NotEqual(t, http.StatusNotFound, w.Code)
	assert.NotEqual(t, http.StatusMethodNotAllowed, w.Code)
}

func TestGetLogs_DefaultPageSize(t *testing.T) {
	logger, _ := zap.NewDevelopment()
	h := New(service.New(nil, logger), logger)
	r := recoveryRouter(h)

	req := httptest.NewRequest(http.MethodGet, "/api/v1/notifications/logs", nil)
	w := httptest.NewRecorder()

	r.ServeHTTP(w, req)
	assert.NotEqual(t, http.StatusNotFound, w.Code)
}
