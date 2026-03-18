package middleware

import (
	"context"
	"net/http"
	"time"

	"github.com/google/uuid"
	"go.uber.org/zap"
)

type contextKeyType string

const requestIDKey contextKeyType = "requestId"

// RequestIDFromContext extracts the request ID from context.
func RequestIDFromContext(ctx context.Context) string {
	if v, ok := ctx.Value(requestIDKey).(string); ok {
		return v
	}
	return ""
}

// statusWriter wraps http.ResponseWriter to capture the status code.
type statusWriter struct {
	http.ResponseWriter
	status int
}

func (w *statusWriter) WriteHeader(status int) {
	w.status = status
	w.ResponseWriter.WriteHeader(status)
}

// Logging returns a middleware that:
// 1. Extracts or generates X-Request-ID
// 2. Sets it in context and response header
// 3. Logs access line with method, URI, status, duration
//
// Port of Java MdcLoggingFilter.java.
func Logging(logger *zap.Logger, serviceName string) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			start := time.Now()

			// Extract or generate request ID
			requestID := r.Header.Get("X-Request-ID")
			if requestID == "" {
				requestID = uuid.New().String()
			}

			// Set in context
			ctx := context.WithValue(r.Context(), requestIDKey, requestID)

			// Echo back in response
			w.Header().Set("X-Request-ID", requestID)

			sw := &statusWriter{ResponseWriter: w, status: http.StatusOK}

			next.ServeHTTP(sw, r.WithContext(ctx))

			duration := time.Since(start)
			logger.Info("request",
				zap.String("method", r.Method),
				zap.String("uri", r.RequestURI),
				zap.Int("status", sw.status),
				zap.Duration("duration", duration),
				zap.String("requestId", requestID),
				zap.String("service", serviceName),
			)
		})
	}
}
