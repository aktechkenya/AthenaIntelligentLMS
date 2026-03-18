package handler

import (
	"encoding/json"
	"net/http"
	"strconv"

	"time"

	"github.com/go-chi/chi/v5"
	"github.com/google/uuid"
	"go.uber.org/zap"

	"github.com/athena-lms/go-services/internal/account/model"
	"github.com/athena-lms/go-services/internal/account/repository"
	"github.com/athena-lms/go-services/internal/account/service"
	"github.com/athena-lms/go-services/internal/common/auth"
	"github.com/athena-lms/go-services/internal/common/errors"
	"github.com/athena-lms/go-services/internal/common/httputil"
)

// Handler handles HTTP requests for the account service.
type Handler struct {
	accountSvc  *service.AccountService
	customerSvc *service.CustomerService
	transferSvc *service.TransferService
	repo        *repository.Repository
	logger      *zap.Logger
}

// New creates a new Handler.
func New(accountSvc *service.AccountService, customerSvc *service.CustomerService, transferSvc *service.TransferService, logger *zap.Logger) *Handler {
	return &Handler{accountSvc: accountSvc, customerSvc: customerSvc, transferSvc: transferSvc, logger: logger}
}

// NewWithRepo creates a new Handler with direct repository access (needed for org settings).
func NewWithRepo(accountSvc *service.AccountService, customerSvc *service.CustomerService, transferSvc *service.TransferService, repo *repository.Repository, logger *zap.Logger) *Handler {
	return &Handler{accountSvc: accountSvc, customerSvc: customerSvc, transferSvc: transferSvc, repo: repo, logger: logger}
}

// RegisterRoutes registers all account service routes.
func (h *Handler) RegisterRoutes(r chi.Router) {
	r.Route("/api/v1/accounts", func(r chi.Router) {
		r.Post("/", h.CreateAccount)
		r.Get("/", h.ListAccounts)
		r.Get("/search", h.SearchAccounts)
		r.Get("/customer/{customerId}", h.GetAccountsByCustomer)
		r.Get("/{id}", h.GetAccount)
		r.Get("/{id}/balance", h.GetBalance)
		r.Post("/{id}/credit", h.Credit)
		r.Post("/{id}/debit", h.Debit)
		r.Get("/{id}/transactions", h.GetTransactions)
		r.Get("/{id}/mini-statement", h.GetMiniStatement)
		r.Get("/{id}/statement", h.GetStatement)
		r.Put("/{id}/status", h.UpdateStatus)
	})
	r.Route("/api/v1/customers", func(r chi.Router) {
		r.Post("/", h.CreateCustomer)
		r.Get("/", h.ListCustomers)
		r.Get("/search", h.SearchCustomers)
		r.Get("/by-customer-id/{customerId}", h.GetCustomerByCustomerId)
		r.Get("/{id}", h.GetCustomer)
		r.Put("/{id}", h.UpdateCustomer)
		r.Patch("/{id}/status", h.UpdateCustomerStatus)
	})
	r.Route("/api/v1/transfers", func(r chi.Router) {
		r.Post("/", h.InitiateTransfer)
		r.Get("/{id}", h.GetTransfer)
		r.Get("/account/{accountId}", h.GetTransfersByAccount)
	})
	r.Route("/api/v1/organization", func(r chi.Router) {
		r.Get("/settings", h.GetOrgSettings)
		r.Put("/settings", h.UpdateOrgSettings)
	})
}

// --- Account ---

func (h *Handler) CreateAccount(w http.ResponseWriter, r *http.Request) {
	tenantID := auth.TenantIDOrDefault(r.Context())
	var req service.CreateAccountRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		httputil.WriteBadRequest(w, "Invalid request body", r.URL.Path)
		return
	}
	resp, err := h.accountSvc.CreateAccount(r.Context(), req, tenantID)
	if err != nil {
		h.handleError(w, r, err)
		return
	}
	httputil.WriteJSON(w, http.StatusCreated, resp)
}

func (h *Handler) ListAccounts(w http.ResponseWriter, r *http.Request) {
	tenantID := auth.TenantIDOrDefault(r.Context())
	page, size := parsePagination(r)
	resp, err := h.accountSvc.ListAccounts(r.Context(), tenantID, page, size)
	if err != nil {
		h.handleError(w, r, err)
		return
	}
	httputil.WriteJSON(w, http.StatusOK, resp)
}

func (h *Handler) GetAccount(w http.ResponseWriter, r *http.Request) {
	tenantID := auth.TenantIDOrDefault(r.Context())
	id, err := uuid.Parse(chi.URLParam(r, "id"))
	if err != nil {
		httputil.WriteBadRequest(w, "Invalid account ID", r.URL.Path)
		return
	}
	resp, err := h.accountSvc.GetAccount(r.Context(), id, tenantID)
	if err != nil {
		h.handleError(w, r, err)
		return
	}
	httputil.WriteJSON(w, http.StatusOK, resp)
}

func (h *Handler) GetBalance(w http.ResponseWriter, r *http.Request) {
	tenantID := auth.TenantIDOrDefault(r.Context())
	id, err := uuid.Parse(chi.URLParam(r, "id"))
	if err != nil {
		httputil.WriteBadRequest(w, "Invalid account ID", r.URL.Path)
		return
	}
	resp, err := h.accountSvc.GetBalance(r.Context(), id, tenantID)
	if err != nil {
		h.handleError(w, r, err)
		return
	}
	httputil.WriteJSON(w, http.StatusOK, resp)
}

func (h *Handler) Credit(w http.ResponseWriter, r *http.Request) {
	tenantID := auth.TenantIDOrDefault(r.Context())
	id, err := uuid.Parse(chi.URLParam(r, "id"))
	if err != nil {
		httputil.WriteBadRequest(w, "Invalid account ID", r.URL.Path)
		return
	}
	var req service.TransactionRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		httputil.WriteBadRequest(w, "Invalid request body", r.URL.Path)
		return
	}
	resp, err := h.accountSvc.Credit(r.Context(), id, req, tenantID)
	if err != nil {
		h.handleError(w, r, err)
		return
	}
	httputil.WriteJSON(w, http.StatusOK, resp)
}

func (h *Handler) Debit(w http.ResponseWriter, r *http.Request) {
	tenantID := auth.TenantIDOrDefault(r.Context())
	id, err := uuid.Parse(chi.URLParam(r, "id"))
	if err != nil {
		httputil.WriteBadRequest(w, "Invalid account ID", r.URL.Path)
		return
	}
	var req service.TransactionRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		httputil.WriteBadRequest(w, "Invalid request body", r.URL.Path)
		return
	}
	resp, err := h.accountSvc.Debit(r.Context(), id, req, tenantID)
	if err != nil {
		h.handleError(w, r, err)
		return
	}
	httputil.WriteJSON(w, http.StatusOK, resp)
}

func (h *Handler) GetTransactions(w http.ResponseWriter, r *http.Request) {
	tenantID := auth.TenantIDOrDefault(r.Context())
	id, err := uuid.Parse(chi.URLParam(r, "id"))
	if err != nil {
		httputil.WriteBadRequest(w, "Invalid account ID", r.URL.Path)
		return
	}
	page, size := parsePagination(r)
	resp, err := h.accountSvc.GetTransactionHistory(r.Context(), id, tenantID, page, size)
	if err != nil {
		h.handleError(w, r, err)
		return
	}
	httputil.WriteJSON(w, http.StatusOK, resp)
}

func (h *Handler) GetMiniStatement(w http.ResponseWriter, r *http.Request) {
	tenantID := auth.TenantIDOrDefault(r.Context())
	id, err := uuid.Parse(chi.URLParam(r, "id"))
	if err != nil {
		httputil.WriteBadRequest(w, "Invalid account ID", r.URL.Path)
		return
	}
	resp, err := h.accountSvc.GetMiniStatement(r.Context(), id, tenantID, 10)
	if err != nil {
		h.handleError(w, r, err)
		return
	}
	httputil.WriteJSON(w, http.StatusOK, resp)
}

func (h *Handler) GetStatement(w http.ResponseWriter, r *http.Request) {
	tenantID := auth.TenantIDOrDefault(r.Context())
	id, err := uuid.Parse(chi.URLParam(r, "id"))
	if err != nil {
		httputil.WriteBadRequest(w, "Invalid account ID", r.URL.Path)
		return
	}
	from, _ := time.Parse("2006-01-02", r.URL.Query().Get("startDate"))
	to, _ := time.Parse("2006-01-02", r.URL.Query().Get("endDate"))
	if from.IsZero() {
		from = time.Now().AddDate(0, -1, 0)
	}
	if to.IsZero() {
		to = time.Now()
	}
	page, size := parsePagination(r)
	resp, err := h.accountSvc.GetStatement(r.Context(), id, tenantID, from, to, page, size)
	if err != nil {
		h.handleError(w, r, err)
		return
	}
	httputil.WriteJSON(w, http.StatusOK, resp)
}

func (h *Handler) SearchAccounts(w http.ResponseWriter, r *http.Request) {
	tenantID := auth.TenantIDOrDefault(r.Context())
	q := r.URL.Query().Get("q")
	resp, err := h.accountSvc.SearchAccounts(r.Context(), q, tenantID)
	if err != nil {
		h.handleError(w, r, err)
		return
	}
	httputil.WriteJSON(w, http.StatusOK, resp)
}

func (h *Handler) GetAccountsByCustomer(w http.ResponseWriter, r *http.Request) {
	tenantID := auth.TenantIDOrDefault(r.Context())
	customerID := chi.URLParam(r, "customerId")
	resp, err := h.accountSvc.GetByCustomerID(r.Context(), customerID, tenantID)
	if err != nil {
		h.handleError(w, r, err)
		return
	}
	httputil.WriteJSON(w, http.StatusOK, resp)
}

func (h *Handler) UpdateStatus(w http.ResponseWriter, r *http.Request) {
	tenantID := auth.TenantIDOrDefault(r.Context())
	id, err := uuid.Parse(chi.URLParam(r, "id"))
	if err != nil {
		httputil.WriteBadRequest(w, "Invalid account ID", r.URL.Path)
		return
	}
	var req struct {
		Status string `json:"status"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		httputil.WriteBadRequest(w, "Invalid request body", r.URL.Path)
		return
	}
	resp, err := h.accountSvc.UpdateStatus(r.Context(), id, req.Status, tenantID)
	if err != nil {
		h.handleError(w, r, err)
		return
	}
	httputil.WriteJSON(w, http.StatusOK, resp)
}

// --- Customer ---

func (h *Handler) CreateCustomer(w http.ResponseWriter, r *http.Request) {
	tenantID := auth.TenantIDOrDefault(r.Context())
	var req service.CreateCustomerRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		httputil.WriteBadRequest(w, "Invalid request body", r.URL.Path)
		return
	}
	resp, err := h.customerSvc.CreateCustomer(r.Context(), req, tenantID)
	if err != nil {
		h.handleError(w, r, err)
		return
	}
	httputil.WriteJSON(w, http.StatusCreated, resp)
}

func (h *Handler) ListCustomers(w http.ResponseWriter, r *http.Request) {
	tenantID := auth.TenantIDOrDefault(r.Context())
	page, size := parsePagination(r)
	resp, err := h.customerSvc.ListCustomers(r.Context(), tenantID, page, size)
	if err != nil {
		h.handleError(w, r, err)
		return
	}
	httputil.WriteJSON(w, http.StatusOK, resp)
}

func (h *Handler) GetCustomer(w http.ResponseWriter, r *http.Request) {
	tenantID := auth.TenantIDOrDefault(r.Context())
	id, err := uuid.Parse(chi.URLParam(r, "id"))
	if err != nil {
		httputil.WriteBadRequest(w, "Invalid customer ID", r.URL.Path)
		return
	}
	resp, err := h.customerSvc.GetCustomer(r.Context(), id, tenantID)
	if err != nil {
		h.handleError(w, r, err)
		return
	}
	httputil.WriteJSON(w, http.StatusOK, resp)
}

func (h *Handler) UpdateCustomer(w http.ResponseWriter, r *http.Request) {
	tenantID := auth.TenantIDOrDefault(r.Context())
	id, err := uuid.Parse(chi.URLParam(r, "id"))
	if err != nil {
		httputil.WriteBadRequest(w, "Invalid customer ID", r.URL.Path)
		return
	}
	var req service.UpdateCustomerRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		httputil.WriteBadRequest(w, "Invalid request body", r.URL.Path)
		return
	}
	resp, err := h.customerSvc.UpdateCustomer(r.Context(), id, req, tenantID)
	if err != nil {
		h.handleError(w, r, err)
		return
	}
	httputil.WriteJSON(w, http.StatusOK, resp)
}

func (h *Handler) UpdateCustomerStatus(w http.ResponseWriter, r *http.Request) {
	tenantID := auth.TenantIDOrDefault(r.Context())
	id, err := uuid.Parse(chi.URLParam(r, "id"))
	if err != nil {
		httputil.WriteBadRequest(w, "Invalid customer ID", r.URL.Path)
		return
	}
	var req struct {
		Status string `json:"status"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		httputil.WriteBadRequest(w, "Invalid request body", r.URL.Path)
		return
	}
	resp, err := h.customerSvc.UpdateCustomerStatus(r.Context(), id, req.Status, tenantID)
	if err != nil {
		h.handleError(w, r, err)
		return
	}
	httputil.WriteJSON(w, http.StatusOK, resp)
}

func (h *Handler) SearchCustomers(w http.ResponseWriter, r *http.Request) {
	tenantID := auth.TenantIDOrDefault(r.Context())
	q := r.URL.Query().Get("q")
	resp, err := h.customerSvc.SearchCustomers(r.Context(), q, tenantID)
	if err != nil {
		h.handleError(w, r, err)
		return
	}
	httputil.WriteJSON(w, http.StatusOK, resp)
}

func (h *Handler) GetCustomerByCustomerId(w http.ResponseWriter, r *http.Request) {
	tenantID := auth.TenantIDOrDefault(r.Context())
	customerID := chi.URLParam(r, "customerId")
	resp, err := h.customerSvc.GetByCustomerID(r.Context(), customerID, tenantID)
	if err != nil {
		h.handleError(w, r, err)
		return
	}
	httputil.WriteJSON(w, http.StatusOK, resp)
}

// --- Transfer ---

func (h *Handler) InitiateTransfer(w http.ResponseWriter, r *http.Request) {
	tenantID := auth.TenantIDOrDefault(r.Context())
	userID := auth.UserIDFromContext(r.Context())
	var req service.TransferRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		httputil.WriteBadRequest(w, "Invalid request body", r.URL.Path)
		return
	}
	resp, err := h.transferSvc.InitiateTransfer(r.Context(), req, tenantID, userID)
	if err != nil {
		h.handleError(w, r, err)
		return
	}
	httputil.WriteJSON(w, http.StatusCreated, resp)
}

func (h *Handler) GetTransfer(w http.ResponseWriter, r *http.Request) {
	tenantID := auth.TenantIDOrDefault(r.Context())
	id, err := uuid.Parse(chi.URLParam(r, "id"))
	if err != nil {
		httputil.WriteBadRequest(w, "Invalid transfer ID", r.URL.Path)
		return
	}
	resp, err := h.transferSvc.GetTransfer(r.Context(), id, tenantID)
	if err != nil {
		h.handleError(w, r, err)
		return
	}
	httputil.WriteJSON(w, http.StatusOK, resp)
}

func (h *Handler) GetTransfersByAccount(w http.ResponseWriter, r *http.Request) {
	tenantID := auth.TenantIDOrDefault(r.Context())
	accountID, err := uuid.Parse(chi.URLParam(r, "accountId"))
	if err != nil {
		httputil.WriteBadRequest(w, "Invalid account ID", r.URL.Path)
		return
	}
	page, size := parsePagination(r)
	resp, err := h.transferSvc.GetTransfersByAccount(r.Context(), accountID, tenantID, page, size)
	if err != nil {
		h.handleError(w, r, err)
		return
	}
	httputil.WriteJSON(w, http.StatusOK, resp)
}

// --- Organization Settings ---

func (h *Handler) GetOrgSettings(w http.ResponseWriter, r *http.Request) {
	tenantID := auth.TenantIDOrDefault(r.Context())
	settings, err := h.repo.GetTenantSettings(r.Context(), tenantID)
	if err != nil {
		// If not found, return defaults
		settings = &model.TenantSettings{
			TenantID: tenantID,
			Currency: "KES",
			Timezone: "Africa/Nairobi",
		}
	}
	httputil.WriteJSON(w, http.StatusOK, settings)
}

func (h *Handler) UpdateOrgSettings(w http.ResponseWriter, r *http.Request) {
	tenantID := auth.TenantIDOrDefault(r.Context())
	var req struct {
		OrgName     *string `json:"orgName"`
		CountryCode *string `json:"countryCode"`
		Currency    *string `json:"currency"`
		Timezone    *string `json:"timezone"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		httputil.WriteBadRequest(w, "Invalid request body", r.URL.Path)
		return
	}

	// Start from existing or defaults
	settings, err := h.repo.GetTenantSettings(r.Context(), tenantID)
	if err != nil {
		settings = &model.TenantSettings{
			TenantID: tenantID,
			Currency: "KES",
			Timezone: "Africa/Nairobi",
		}
	}

	// Apply updates
	if req.OrgName != nil {
		settings.OrgName = req.OrgName
	}
	if req.CountryCode != nil {
		settings.CountryCode = req.CountryCode
	}
	if req.Currency != nil {
		settings.Currency = *req.Currency
	}
	if req.Timezone != nil {
		settings.Timezone = *req.Timezone
	}

	if err := h.repo.UpsertTenantSettings(r.Context(), settings); err != nil {
		h.logger.Error("Failed to update tenant settings", zap.Error(err))
		httputil.WriteInternalError(w, "Failed to save settings", r.URL.Path)
		return
	}

	// Re-read to get the full record with timestamps
	saved, err := h.repo.GetTenantSettings(r.Context(), tenantID)
	if err != nil {
		h.logger.Error("Failed to read back tenant settings", zap.Error(err))
		httputil.WriteJSON(w, http.StatusOK, settings)
		return
	}
	httputil.WriteJSON(w, http.StatusOK, saved)
}

// --- Helpers ---

func (h *Handler) handleError(w http.ResponseWriter, r *http.Request, err error) {
	switch e := err.(type) {
	case *errors.NotFoundError:
		httputil.WriteNotFound(w, e.Message, r.URL.Path)
	case *errors.BusinessError:
		httputil.WriteErrorJSON(w, e.StatusCode, http.StatusText(e.StatusCode), e.Message, r.URL.Path)
	default:
		h.logger.Error("Internal error", zap.Error(err), zap.String("path", r.URL.Path))
		httputil.WriteInternalError(w, "An unexpected error occurred", r.URL.Path)
	}
}

func parsePagination(r *http.Request) (int, int) {
	page, _ := strconv.Atoi(r.URL.Query().Get("page"))
	size, _ := strconv.Atoi(r.URL.Query().Get("size"))
	if size <= 0 || size > 100 {
		size = 20
	}
	if page < 0 {
		page = 0
	}
	return page, size
}
