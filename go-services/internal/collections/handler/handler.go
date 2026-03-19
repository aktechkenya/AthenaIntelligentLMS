package handler

import (
	"encoding/json"
	"net/http"
	"strconv"

	"github.com/go-chi/chi/v5"
	"github.com/google/uuid"
	"go.uber.org/zap"

	"github.com/athena-lms/go-services/internal/collections/model"
	"github.com/athena-lms/go-services/internal/collections/service"
	"github.com/athena-lms/go-services/internal/common/auth"
	"github.com/athena-lms/go-services/internal/common/dto"
	"github.com/athena-lms/go-services/internal/common/httputil"
	"github.com/athena-lms/go-services/internal/common/middleware"
)

// Handler provides HTTP handlers for the collections API.
type Handler struct {
	svc    *service.CollectionsService
	logger *zap.Logger
}

// NewHandler creates a new collections handler.
func NewHandler(svc *service.CollectionsService, logger *zap.Logger) *Handler {
	return &Handler{svc: svc, logger: logger}
}

// Routes registers all collection routes on the given chi.Router.
func (h *Handler) Routes(r chi.Router) {
	r.Route("/api/v1/collections", func(r chi.Router) {
		// Summary
		r.Get("/summary", h.GetSummary)

		// Cases
		r.Get("/cases", h.ListCases)
		r.Get("/cases/overdue-followups", h.GetOverdueFollowUps)
		r.Get("/cases/loan/{loanId}", h.GetCaseByLoan)
		r.Get("/cases/{id}/detail", h.GetCaseDetail)
		r.Get("/cases/{id}/actions", h.ListActions)
		r.Get("/cases/{id}/ptps", h.ListPtps)
		r.Get("/cases/{id}/recommended-actions", h.GetRecommendedActions)
		r.Get("/cases/{id}", h.GetCase)
		r.Put("/cases/{id}", h.UpdateCase)
		r.Post("/cases/{id}/close", h.CloseCase)
		r.Post("/cases/{id}/actions", h.AddAction)
		r.Post("/cases/{id}/ptps", h.AddPtp)

		// Strategies
		r.Get("/strategies", h.ListStrategies)
		r.Post("/strategies", h.CreateStrategy)
		r.Put("/strategies/{id}", h.UpdateStrategy)
		r.Delete("/strategies/{id}", h.DeleteStrategy)
	})
}

// GetSummary returns collection summary for the tenant.
func (h *Handler) GetSummary(w http.ResponseWriter, r *http.Request) {
	tenantID := tenantID(r)
	summary, err := h.svc.GetSummary(r.Context(), tenantID)
	if err != nil {
		middleware.HandleError(w, r, err)
		return
	}
	httputil.WriteJSON(w, http.StatusOK, summary)
}

// ListCases returns a paginated list of collection cases with optional filtering.
func (h *Handler) ListCases(w http.ResponseWriter, r *http.Request) {
	tenantID := tenantID(r)
	page := queryInt(r, "page", 0)
	size := queryInt(r, "size", 20)

	var statusPtr *model.CaseStatus
	if s := r.URL.Query().Get("status"); s != "" {
		st := model.CaseStatus(s)
		statusPtr = &st
	}

	filters := service.CaseFilterParams{
		Stage:      r.URL.Query().Get("stage"),
		Priority:   r.URL.Query().Get("priority"),
		AssignedTo: r.URL.Query().Get("assignedTo"),
		Search:     r.URL.Query().Get("search"),
		Sort:       r.URL.Query().Get("sort"),
		Dir:        r.URL.Query().Get("dir"),
		MinDPD:     queryInt(r, "minDpd", 0),
		MaxDPD:     queryInt(r, "maxDpd", 0),
	}

	result, err := h.svc.ListCases(r.Context(), tenantID, statusPtr, filters, page, size)
	if err != nil {
		middleware.HandleError(w, r, err)
		return
	}

	resp := dto.NewPageResponse(result.Content, result.Page, result.Size, result.TotalElements)
	httputil.WriteJSON(w, http.StatusOK, resp)
}

// GetCaseDetail returns a composite response with case, actions, and PTPs.
func (h *Handler) GetCaseDetail(w http.ResponseWriter, r *http.Request) {
	id, err := uuidParam(r, "id")
	if err != nil {
		httputil.WriteBadRequest(w, "Invalid case ID", r.URL.Path)
		return
	}
	tenantID := tenantID(r)
	resp, err := h.svc.GetCaseDetail(r.Context(), id, tenantID)
	if err != nil {
		middleware.HandleError(w, r, err)
		return
	}
	httputil.WriteJSON(w, http.StatusOK, resp)
}

// GetCase returns a single collection case.
func (h *Handler) GetCase(w http.ResponseWriter, r *http.Request) {
	id, err := uuidParam(r, "id")
	if err != nil {
		httputil.WriteBadRequest(w, "Invalid case ID", r.URL.Path)
		return
	}
	tenantID := tenantID(r)
	resp, err := h.svc.GetCase(r.Context(), id, tenantID)
	if err != nil {
		middleware.HandleError(w, r, err)
		return
	}
	httputil.WriteJSON(w, http.StatusOK, resp)
}

// GetCaseByLoan returns the collection case for a loan.
func (h *Handler) GetCaseByLoan(w http.ResponseWriter, r *http.Request) {
	loanID, err := uuidParam(r, "loanId")
	if err != nil {
		httputil.WriteBadRequest(w, "Invalid loan ID", r.URL.Path)
		return
	}
	tenantID := tenantID(r)
	resp, err := h.svc.GetCaseByLoan(r.Context(), loanID, tenantID)
	if err != nil {
		middleware.HandleError(w, r, err)
		return
	}
	httputil.WriteJSON(w, http.StatusOK, resp)
}

// UpdateCase updates a collection case.
func (h *Handler) UpdateCase(w http.ResponseWriter, r *http.Request) {
	id, err := uuidParam(r, "id")
	if err != nil {
		httputil.WriteBadRequest(w, "Invalid case ID", r.URL.Path)
		return
	}
	var req model.UpdateCaseRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		httputil.WriteBadRequest(w, "Invalid request body", r.URL.Path)
		return
	}
	tenantID := tenantID(r)
	resp, err := h.svc.UpdateCase(r.Context(), id, req, tenantID)
	if err != nil {
		middleware.HandleError(w, r, err)
		return
	}
	httputil.WriteJSON(w, http.StatusOK, resp)
}

// CloseCase closes a collection case.
func (h *Handler) CloseCase(w http.ResponseWriter, r *http.Request) {
	id, err := uuidParam(r, "id")
	if err != nil {
		httputil.WriteBadRequest(w, "Invalid case ID", r.URL.Path)
		return
	}
	tenantID := tenantID(r)
	resp, err := h.svc.CloseCase(r.Context(), id, tenantID)
	if err != nil {
		middleware.HandleError(w, r, err)
		return
	}
	httputil.WriteJSON(w, http.StatusOK, resp)
}

// AddAction adds an action to a collection case.
func (h *Handler) AddAction(w http.ResponseWriter, r *http.Request) {
	id, err := uuidParam(r, "id")
	if err != nil {
		httputil.WriteBadRequest(w, "Invalid case ID", r.URL.Path)
		return
	}
	var req model.AddActionRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		httputil.WriteBadRequest(w, "Invalid request body", r.URL.Path)
		return
	}
	if req.ActionType == "" {
		httputil.WriteBadRequest(w, "actionType is required", r.URL.Path)
		return
	}
	tenantID := tenantID(r)
	resp, err := h.svc.AddAction(r.Context(), id, req, tenantID)
	if err != nil {
		middleware.HandleError(w, r, err)
		return
	}
	httputil.WriteJSON(w, http.StatusCreated, resp)
}

// ListActions lists all actions for a collection case.
func (h *Handler) ListActions(w http.ResponseWriter, r *http.Request) {
	id, err := uuidParam(r, "id")
	if err != nil {
		httputil.WriteBadRequest(w, "Invalid case ID", r.URL.Path)
		return
	}
	tenantID := tenantID(r)
	resp, err := h.svc.ListActions(r.Context(), id, tenantID)
	if err != nil {
		middleware.HandleError(w, r, err)
		return
	}
	httputil.WriteJSON(w, http.StatusOK, resp)
}

// AddPtp adds a promise to pay to a collection case.
func (h *Handler) AddPtp(w http.ResponseWriter, r *http.Request) {
	id, err := uuidParam(r, "id")
	if err != nil {
		httputil.WriteBadRequest(w, "Invalid case ID", r.URL.Path)
		return
	}
	var req model.AddPtpRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		httputil.WriteBadRequest(w, "Invalid request body", r.URL.Path)
		return
	}
	if req.PromiseDate == "" {
		httputil.WriteBadRequest(w, "promiseDate is required", r.URL.Path)
		return
	}
	if req.PromisedAmount.IsZero() || req.PromisedAmount.IsNegative() {
		httputil.WriteBadRequest(w, "promisedAmount must be at least 0.01", r.URL.Path)
		return
	}
	tenantID := tenantID(r)
	resp, err := h.svc.AddPtp(r.Context(), id, req, tenantID)
	if err != nil {
		middleware.HandleError(w, r, err)
		return
	}
	httputil.WriteJSON(w, http.StatusCreated, resp)
}

// ListPtps lists all promises to pay for a collection case.
func (h *Handler) ListPtps(w http.ResponseWriter, r *http.Request) {
	id, err := uuidParam(r, "id")
	if err != nil {
		httputil.WriteBadRequest(w, "Invalid case ID", r.URL.Path)
		return
	}
	tenantID := tenantID(r)
	resp, err := h.svc.ListPtps(r.Context(), id, tenantID)
	if err != nil {
		middleware.HandleError(w, r, err)
		return
	}
	httputil.WriteJSON(w, http.StatusOK, resp)
}

// -----------------------------------------------------------------------
// Helpers
// -----------------------------------------------------------------------

func tenantID(r *http.Request) string {
	return auth.TenantIDOrDefault(r.Context())
}

func uuidParam(r *http.Request, name string) (uuid.UUID, error) {
	return uuid.Parse(chi.URLParam(r, name))
}

func queryInt(r *http.Request, key string, fallback int) int {
	s := r.URL.Query().Get(key)
	if s == "" {
		return fallback
	}
	n, err := strconv.Atoi(s)
	if err != nil || n < 0 {
		return fallback
	}
	return n
}

// -----------------------------------------------------------------------
// Strategy handlers
// -----------------------------------------------------------------------

// ListStrategies returns all collection strategies for the tenant.
func (h *Handler) ListStrategies(w http.ResponseWriter, r *http.Request) {
	tenantID := tenantID(r)
	resp, err := h.svc.ListStrategies(r.Context(), tenantID)
	if err != nil {
		middleware.HandleError(w, r, err)
		return
	}
	httputil.WriteJSON(w, http.StatusOK, resp)
}

// CreateStrategy creates a new collection strategy.
func (h *Handler) CreateStrategy(w http.ResponseWriter, r *http.Request) {
	var req model.CreateStrategyRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		httputil.WriteBadRequest(w, "Invalid request body", r.URL.Path)
		return
	}
	tenantID := tenantID(r)
	resp, err := h.svc.CreateStrategy(r.Context(), req, tenantID)
	if err != nil {
		middleware.HandleError(w, r, err)
		return
	}
	httputil.WriteJSON(w, http.StatusCreated, resp)
}

// UpdateStrategy updates an existing collection strategy.
func (h *Handler) UpdateStrategy(w http.ResponseWriter, r *http.Request) {
	id, err := uuidParam(r, "id")
	if err != nil {
		httputil.WriteBadRequest(w, "Invalid strategy ID", r.URL.Path)
		return
	}
	var req model.UpdateStrategyRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		httputil.WriteBadRequest(w, "Invalid request body", r.URL.Path)
		return
	}
	tenantID := tenantID(r)
	resp, err := h.svc.UpdateStrategy(r.Context(), id, req, tenantID)
	if err != nil {
		middleware.HandleError(w, r, err)
		return
	}
	httputil.WriteJSON(w, http.StatusOK, resp)
}

// DeleteStrategy deletes a collection strategy.
func (h *Handler) DeleteStrategy(w http.ResponseWriter, r *http.Request) {
	id, err := uuidParam(r, "id")
	if err != nil {
		httputil.WriteBadRequest(w, "Invalid strategy ID", r.URL.Path)
		return
	}
	tenantID := tenantID(r)
	if err := h.svc.DeleteStrategy(r.Context(), id, tenantID); err != nil {
		middleware.HandleError(w, r, err)
		return
	}
	httputil.WriteJSON(w, http.StatusNoContent, nil)
}

// GetRecommendedActions returns strategy-driven recommended actions for a case.
func (h *Handler) GetRecommendedActions(w http.ResponseWriter, r *http.Request) {
	id, err := uuidParam(r, "id")
	if err != nil {
		httputil.WriteBadRequest(w, "Invalid case ID", r.URL.Path)
		return
	}
	tenantID := tenantID(r)
	resp, err := h.svc.EvaluateStrategies(r.Context(), id, tenantID)
	if err != nil {
		middleware.HandleError(w, r, err)
		return
	}
	httputil.WriteJSON(w, http.StatusOK, resp)
}

// GetOverdueFollowUps returns cases with overdue follow-up actions.
func (h *Handler) GetOverdueFollowUps(w http.ResponseWriter, r *http.Request) {
	tenantID := tenantID(r)
	resp, err := h.svc.GetOverdueFollowUps(r.Context(), tenantID)
	if err != nil {
		middleware.HandleError(w, r, err)
		return
	}
	httputil.WriteJSON(w, http.StatusOK, resp)
}
