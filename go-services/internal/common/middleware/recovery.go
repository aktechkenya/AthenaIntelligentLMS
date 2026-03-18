package middleware

import (
	"net/http"

	"go.uber.org/zap"

	"github.com/athena-lms/go-services/internal/common/httputil"
)

// Recovery returns a middleware that recovers from panics and returns a 500 error.
func Recovery(logger *zap.Logger) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			defer func() {
				if rec := recover(); rec != nil {
					logger.Error("panic recovered",
						zap.Any("error", rec),
						zap.String("path", r.URL.Path),
					)
					httputil.WriteInternalError(w, "An unexpected error occurred", r.URL.Path)
				}
			}()
			next.ServeHTTP(w, r)
		})
	}
}
