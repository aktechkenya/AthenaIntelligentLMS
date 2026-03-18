package client

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/google/uuid"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"go.uber.org/zap"

	"github.com/athena-lms/go-services/internal/common/errors"
	"github.com/athena-lms/go-services/internal/common/httputil"
)

func TestValidateLoanExists_NilLoanID(t *testing.T) {
	c := NewLoanManagementClient("", zap.NewNop())
	err := c.ValidateLoanExists(context.Background(), nil)
	assert.NoError(t, err)
}

func TestValidateLoanExists_ActiveLoan(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(map[string]any{"status": "ACTIVE"})
	}))
	defer srv.Close()

	c := &LoanManagementClient{
		client:  httputil.NewServiceClient(""),
		baseURL: srv.URL,
		logger:  zap.NewNop(),
	}

	loanID := uuid.New()
	err := c.ValidateLoanExists(context.Background(), &loanID)
	assert.NoError(t, err)
}

func TestValidateLoanExists_ClosedLoan(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(map[string]any{"status": "CLOSED"})
	}))
	defer srv.Close()

	c := &LoanManagementClient{
		client:  httputil.NewServiceClient(""),
		baseURL: srv.URL,
		logger:  zap.NewNop(),
	}

	loanID := uuid.New()
	err := c.ValidateLoanExists(context.Background(), &loanID)
	require.Error(t, err)

	var bizErr *errors.BusinessError
	assert.ErrorAs(t, err, &bizErr)
	assert.Contains(t, bizErr.Message, "not eligible for payment")
}

func TestValidateLoanExists_WrittenOffLoan(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(map[string]any{"status": "WRITTEN_OFF"})
	}))
	defer srv.Close()

	c := &LoanManagementClient{
		client:  httputil.NewServiceClient(""),
		baseURL: srv.URL,
		logger:  zap.NewNop(),
	}

	loanID := uuid.New()
	err := c.ValidateLoanExists(context.Background(), &loanID)
	require.Error(t, err)

	var bizErr *errors.BusinessError
	assert.ErrorAs(t, err, &bizErr)
	assert.Contains(t, bizErr.Message, "not eligible")
}

func TestValidateLoanExists_ServiceDown_FailsOpen(t *testing.T) {
	c := &LoanManagementClient{
		client:  httputil.NewServiceClient(""),
		baseURL: "http://localhost:1", // unreachable port
		logger:  zap.NewNop(),
	}

	loanID := uuid.New()
	err := c.ValidateLoanExists(context.Background(), &loanID)
	assert.NoError(t, err) // should fail open
}
