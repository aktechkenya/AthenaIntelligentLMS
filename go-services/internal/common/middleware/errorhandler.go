package middleware

import (
	"net/http"

	"github.com/athena-lms/go-services/internal/common/errors"
	"github.com/athena-lms/go-services/internal/common/httputil"
)

// HandleError writes the appropriate error response based on the error type.
// Port of Java GlobalExceptionHandler.java.
func HandleError(w http.ResponseWriter, r *http.Request, err error) {
	switch e := err.(type) {
	case *errors.NotFoundError:
		httputil.WriteNotFound(w, e.Message, r.URL.Path)
	case *errors.BusinessError:
		statusText := http.StatusText(e.StatusCode)
		httputil.WriteErrorJSON(w, e.StatusCode, statusText, e.Message, r.URL.Path)
	default:
		httputil.WriteInternalError(w, "An unexpected error occurred", r.URL.Path)
	}
}
