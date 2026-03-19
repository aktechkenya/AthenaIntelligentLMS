package handler

import (
	"encoding/json"
	"net/http"
	"strconv"

	"github.com/go-chi/chi/v5"
	"github.com/google/uuid"
	"go.uber.org/zap"

	"github.com/athena-lms/go-services/internal/common/auth"
	"github.com/athena-lms/go-services/internal/common/dto"
	"github.com/athena-lms/go-services/internal/common/httputil"
	"github.com/athena-lms/go-services/internal/fraud/engine"
	"github.com/athena-lms/go-services/internal/fraud/model"
	"github.com/athena-lms/go-services/internal/fraud/service"
)

// Handler handles HTTP requests for fraud detection.
type Handler struct {
	svc    *service.Service
	eng    *engine.Engine
	logger *zap.Logger
}

// New creates a new Handler.
func New(svc *service.Service, eng *engine.Engine, logger *zap.Logger) *Handler {
	return &Handler{svc: svc, eng: eng, logger: logger}
}

// RegisterRoutes registers all fraud detection routes on the given router.
func (h *Handler) RegisterRoutes(r chi.Router) {
	r.Route("/api/v1/fraud", func(r chi.Router) {
		r.Get("/summary", h.GetSummary)
		r.Get("/analytics", h.GetAnalytics)

		r.Get("/alerts", h.ListAlerts)
		r.Get("/alerts/{id}", h.GetAlert)
		r.Post("/alerts/{id}/resolve", h.ResolveAlert)
		r.Post("/alerts/{id}/assign", h.AssignAlert)
		r.Post("/alerts/bulk/assign", h.BulkAssignAlerts)
		r.Post("/alerts/bulk/resolve", h.BulkResolveAlerts)

		r.Get("/rules", h.ListRules)
		r.Get("/rules/{id}", h.GetRule)
		r.Put("/rules/{id}", h.UpdateRule)

		r.Get("/cases", h.ListCases)
		r.Post("/cases", h.CreateCase)
		r.Get("/cases/{id}", h.GetCase)
		r.Put("/cases/{id}", h.UpdateCase)
		r.Get("/cases/{id}/notes", h.ListCaseNotes)
		r.Post("/cases/{id}/notes", h.AddCaseNote)
		r.Get("/cases/{id}/timeline", h.GetCaseTimeline)

		r.Get("/watchlist", h.ListWatchlistEntries)
		r.Post("/watchlist", h.CreateWatchlistEntry)
		r.Get("/watchlist/{id}", h.GetWatchlistEntry)
		r.Put("/watchlist/{id}/deactivate", h.DeactivateWatchlistEntry)
		r.Post("/watchlist/screen", h.ScreenCustomer)

		r.Get("/events/recent", h.ListRecentEvents)

		r.Get("/sar-reports", h.ListSarReports)
		r.Post("/sar-reports", h.CreateSarReport)
		r.Get("/sar-reports/{id}", h.GetSarReport)
		r.Put("/sar-reports/{id}", h.UpdateSarReport)

		r.Get("/risk-profiles/high-risk", h.ListHighRiskCustomers)
		r.Get("/risk-profiles/{customerId}", h.GetRiskProfile)

		r.Get("/network/{customerId}", h.ListNetworkLinks)

		r.Get("/audit", h.ListAuditLog)

		r.Post("/evaluate", h.EvaluateTransaction)
	})
}

// GetSummary handles GET /api/v1/fraud/summary
func (h *Handler) GetSummary(w http.ResponseWriter, r *http.Request) {
	tenantID := auth.TenantIDOrDefault(r.Context())
	resp, err := h.svc.GetSummary(r.Context(), tenantID)
	if err != nil {
		h.logger.Error("Failed to get fraud summary", zap.Error(err))
		httputil.WriteInternalError(w, "Failed to get fraud summary", r.URL.Path)
		return
	}
	httputil.WriteJSON(w, http.StatusOK, resp)
}

// GetAnalytics handles GET /api/v1/fraud/analytics
func (h *Handler) GetAnalytics(w http.ResponseWriter, r *http.Request) {
	tenantID := auth.TenantIDOrDefault(r.Context())
	resp, err := h.svc.GetAnalytics(r.Context(), tenantID)
	if err != nil {
		h.logger.Error("Failed to get fraud analytics", zap.Error(err))
		httputil.WriteInternalError(w, "Failed to get fraud analytics", r.URL.Path)
		return
	}
	httputil.WriteJSON(w, http.StatusOK, resp)
}

// ListAlerts handles GET /api/v1/fraud/alerts
func (h *Handler) ListAlerts(w http.ResponseWriter, r *http.Request) {
	tenantID := auth.TenantIDOrDefault(r.Context())

	page, _ := strconv.Atoi(r.URL.Query().Get("page"))
	size, _ := strconv.Atoi(r.URL.Query().Get("size"))
	if size <= 0 {
		size = 20
	}

	var status *model.AlertStatus
	if s := r.URL.Query().Get("status"); s != "" {
		st := model.AlertStatus(s)
		status = &st
	}

	alerts, total, err := h.svc.ListAlerts(r.Context(), tenantID, status, page, size)
	if err != nil {
		h.logger.Error("Failed to list alerts", zap.Error(err))
		httputil.WriteInternalError(w, "Failed to list alerts", r.URL.Path)
		return
	}
	if alerts == nil {
		alerts = []*model.FraudAlert{}
	}

	httputil.WriteJSON(w, http.StatusOK, dto.NewPageResponse(alerts, page, size, total))
}

// GetAlert handles GET /api/v1/fraud/alerts/{id}
func (h *Handler) GetAlert(w http.ResponseWriter, r *http.Request) {
	id, err := uuid.Parse(chi.URLParam(r, "id"))
	if err != nil {
		httputil.WriteBadRequest(w, "Invalid alert ID", r.URL.Path)
		return
	}

	alert, err := h.svc.GetAlert(r.Context(), id)
	if err != nil {
		h.logger.Error("Failed to get alert", zap.Error(err))
		httputil.WriteInternalError(w, "Failed to get alert", r.URL.Path)
		return
	}
	if alert == nil {
		httputil.WriteNotFound(w, "Alert not found: "+id.String(), r.URL.Path)
		return
	}

	httputil.WriteJSON(w, http.StatusOK, alert)
}

// ResolveAlert handles POST /api/v1/fraud/alerts/{id}/resolve
func (h *Handler) ResolveAlert(w http.ResponseWriter, r *http.Request) {
	id, err := uuid.Parse(chi.URLParam(r, "id"))
	if err != nil {
		httputil.WriteBadRequest(w, "Invalid alert ID", r.URL.Path)
		return
	}

	var req model.ResolveAlertRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		httputil.WriteBadRequest(w, "Invalid request body", r.URL.Path)
		return
	}

	tenantID := auth.TenantIDOrDefault(r.Context())
	alert, err := h.svc.ResolveAlert(r.Context(), id, req, tenantID)
	if err != nil {
		h.logger.Error("Failed to resolve alert", zap.Error(err))
		httputil.WriteInternalError(w, "Failed to resolve alert", r.URL.Path)
		return
	}
	if alert == nil {
		httputil.WriteNotFound(w, "Alert not found: "+id.String(), r.URL.Path)
		return
	}

	httputil.WriteJSON(w, http.StatusOK, alert)
}

// AssignAlert handles POST /api/v1/fraud/alerts/{id}/assign
func (h *Handler) AssignAlert(w http.ResponseWriter, r *http.Request) {
	id, err := uuid.Parse(chi.URLParam(r, "id"))
	if err != nil {
		httputil.WriteBadRequest(w, "Invalid alert ID", r.URL.Path)
		return
	}

	var req model.AssignAlertRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		httputil.WriteBadRequest(w, "Invalid request body", r.URL.Path)
		return
	}

	alert, err := h.svc.AssignAlert(r.Context(), id, req)
	if err != nil {
		h.logger.Error("Failed to assign alert", zap.Error(err))
		httputil.WriteInternalError(w, "Failed to assign alert", r.URL.Path)
		return
	}
	if alert == nil {
		httputil.WriteNotFound(w, "Alert not found: "+id.String(), r.URL.Path)
		return
	}

	httputil.WriteJSON(w, http.StatusOK, alert)
}

// ListRules handles GET /api/v1/fraud/rules
func (h *Handler) ListRules(w http.ResponseWriter, r *http.Request) {
	tenantID := auth.TenantIDOrDefault(r.Context())
	rules, err := h.svc.ListRules(r.Context(), tenantID)
	if err != nil {
		h.logger.Error("Failed to list rules", zap.Error(err))
		httputil.WriteInternalError(w, "Failed to list rules", r.URL.Path)
		return
	}
	if rules == nil {
		rules = []*model.FraudRule{}
	}

	httputil.WriteJSON(w, http.StatusOK, rules)
}

// GetRule handles GET /api/v1/fraud/rules/{id}
func (h *Handler) GetRule(w http.ResponseWriter, r *http.Request) {
	id, err := uuid.Parse(chi.URLParam(r, "id"))
	if err != nil {
		httputil.WriteBadRequest(w, "Invalid rule ID", r.URL.Path)
		return
	}

	rule, err := h.svc.GetRule(r.Context(), id)
	if err != nil {
		h.logger.Error("Failed to get rule", zap.Error(err))
		httputil.WriteInternalError(w, "Failed to get rule", r.URL.Path)
		return
	}
	if rule == nil {
		httputil.WriteNotFound(w, "Rule not found: "+id.String(), r.URL.Path)
		return
	}

	httputil.WriteJSON(w, http.StatusOK, rule)
}

// UpdateRule handles PUT /api/v1/fraud/rules/{id}
func (h *Handler) UpdateRule(w http.ResponseWriter, r *http.Request) {
	id, err := uuid.Parse(chi.URLParam(r, "id"))
	if err != nil {
		httputil.WriteBadRequest(w, "Invalid rule ID", r.URL.Path)
		return
	}

	var req model.UpdateRuleRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		httputil.WriteBadRequest(w, "Invalid request body", r.URL.Path)
		return
	}

	rule, err := h.svc.UpdateRule(r.Context(), id, req)
	if err != nil {
		h.logger.Error("Failed to update rule", zap.Error(err))
		httputil.WriteInternalError(w, "Failed to update rule", r.URL.Path)
		return
	}
	if rule == nil {
		httputil.WriteNotFound(w, "Rule not found: "+id.String(), r.URL.Path)
		return
	}

	httputil.WriteJSON(w, http.StatusOK, rule)
}

// ListCases handles GET /api/v1/fraud/cases
func (h *Handler) ListCases(w http.ResponseWriter, r *http.Request) {
	tenantID := auth.TenantIDOrDefault(r.Context())

	page, _ := strconv.Atoi(r.URL.Query().Get("page"))
	size, _ := strconv.Atoi(r.URL.Query().Get("size"))
	if size <= 0 {
		size = 20
	}

	var status *model.CaseStatus
	if s := r.URL.Query().Get("status"); s != "" {
		st := model.CaseStatus(s)
		status = &st
	}

	cases, total, err := h.svc.ListCases(r.Context(), tenantID, status, page, size)
	if err != nil {
		h.logger.Error("Failed to list cases", zap.Error(err))
		httputil.WriteInternalError(w, "Failed to list cases", r.URL.Path)
		return
	}
	if cases == nil {
		cases = []*model.FraudCase{}
	}

	httputil.WriteJSON(w, http.StatusOK, dto.NewPageResponse(cases, page, size, total))
}

// GetCase handles GET /api/v1/fraud/cases/{id}
func (h *Handler) GetCase(w http.ResponseWriter, r *http.Request) {
	id, err := uuid.Parse(chi.URLParam(r, "id"))
	if err != nil {
		httputil.WriteBadRequest(w, "Invalid case ID", r.URL.Path)
		return
	}

	fraudCase, err := h.svc.GetCase(r.Context(), id)
	if err != nil {
		h.logger.Error("Failed to get case", zap.Error(err))
		httputil.WriteInternalError(w, "Failed to get case", r.URL.Path)
		return
	}
	if fraudCase == nil {
		httputil.WriteNotFound(w, "Case not found: "+id.String(), r.URL.Path)
		return
	}

	httputil.WriteJSON(w, http.StatusOK, fraudCase)
}

// ListCaseNotes handles GET /api/v1/fraud/cases/{id}/notes
func (h *Handler) ListCaseNotes(w http.ResponseWriter, r *http.Request) {
	id, err := uuid.Parse(chi.URLParam(r, "id"))
	if err != nil {
		httputil.WriteBadRequest(w, "Invalid case ID", r.URL.Path)
		return
	}

	tenantID := auth.TenantIDOrDefault(r.Context())
	notes, err := h.svc.ListCaseNotes(r.Context(), id, tenantID)
	if err != nil {
		h.logger.Error("Failed to list case notes", zap.Error(err))
		httputil.WriteInternalError(w, "Failed to list case notes", r.URL.Path)
		return
	}
	if notes == nil {
		notes = []*model.CaseNote{}
	}

	httputil.WriteJSON(w, http.StatusOK, notes)
}

// AddCaseNote handles POST /api/v1/fraud/cases/{id}/notes
func (h *Handler) AddCaseNote(w http.ResponseWriter, r *http.Request) {
	id, err := uuid.Parse(chi.URLParam(r, "id"))
	if err != nil {
		httputil.WriteBadRequest(w, "Invalid case ID", r.URL.Path)
		return
	}

	var req model.AddCaseNoteRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		httputil.WriteBadRequest(w, "Invalid request body", r.URL.Path)
		return
	}

	tenantID := auth.TenantIDOrDefault(r.Context())
	note, err := h.svc.AddCaseNote(r.Context(), id, req, tenantID)
	if err != nil {
		h.logger.Error("Failed to add case note", zap.Error(err))
		httputil.WriteInternalError(w, "Failed to add case note", r.URL.Path)
		return
	}

	httputil.WriteJSON(w, http.StatusCreated, note)
}

// ListWatchlistEntries handles GET /api/v1/fraud/watchlist
func (h *Handler) ListWatchlistEntries(w http.ResponseWriter, r *http.Request) {
	tenantID := auth.TenantIDOrDefault(r.Context())

	page, _ := strconv.Atoi(r.URL.Query().Get("page"))
	size, _ := strconv.Atoi(r.URL.Query().Get("size"))
	if size <= 0 {
		size = 20
	}

	var active *bool
	if a := r.URL.Query().Get("active"); a != "" {
		val := a == "true"
		active = &val
	}

	entries, total, err := h.svc.ListWatchlistEntries(r.Context(), tenantID, active, page, size)
	if err != nil {
		h.logger.Error("Failed to list watchlist entries", zap.Error(err))
		httputil.WriteInternalError(w, "Failed to list watchlist entries", r.URL.Path)
		return
	}
	if entries == nil {
		entries = []*model.WatchlistEntry{}
	}

	httputil.WriteJSON(w, http.StatusOK, dto.NewPageResponse(entries, page, size, total))
}

// CreateWatchlistEntry handles POST /api/v1/fraud/watchlist
func (h *Handler) CreateWatchlistEntry(w http.ResponseWriter, r *http.Request) {
	var req model.CreateWatchlistEntryRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		httputil.WriteBadRequest(w, "Invalid request body", r.URL.Path)
		return
	}

	tenantID := auth.TenantIDOrDefault(r.Context())
	entry, err := h.svc.CreateWatchlistEntry(r.Context(), req, tenantID)
	if err != nil {
		h.logger.Error("Failed to create watchlist entry", zap.Error(err))
		httputil.WriteInternalError(w, "Failed to create watchlist entry", r.URL.Path)
		return
	}

	httputil.WriteJSON(w, http.StatusCreated, entry)
}

// GetWatchlistEntry handles GET /api/v1/fraud/watchlist/{id}
func (h *Handler) GetWatchlistEntry(w http.ResponseWriter, r *http.Request) {
	id, err := uuid.Parse(chi.URLParam(r, "id"))
	if err != nil {
		httputil.WriteBadRequest(w, "Invalid watchlist entry ID", r.URL.Path)
		return
	}

	entry, err := h.svc.GetWatchlistEntry(r.Context(), id)
	if err != nil {
		h.logger.Error("Failed to get watchlist entry", zap.Error(err))
		httputil.WriteInternalError(w, "Failed to get watchlist entry", r.URL.Path)
		return
	}
	if entry == nil {
		httputil.WriteNotFound(w, "Watchlist entry not found: "+id.String(), r.URL.Path)
		return
	}

	httputil.WriteJSON(w, http.StatusOK, entry)
}

// ListHighRiskCustomers handles GET /api/v1/fraud/risk-profiles/high-risk
func (h *Handler) ListHighRiskCustomers(w http.ResponseWriter, r *http.Request) {
	tenantID := auth.TenantIDOrDefault(r.Context())

	page, _ := strconv.Atoi(r.URL.Query().Get("page"))
	size, _ := strconv.Atoi(r.URL.Query().Get("size"))
	if size <= 0 {
		size = 20
	}

	profiles, total, err := h.svc.ListHighRiskCustomers(r.Context(), tenantID, page, size)
	if err != nil {
		h.logger.Error("Failed to list high risk customers", zap.Error(err))
		httputil.WriteInternalError(w, "Failed to list high risk customers", r.URL.Path)
		return
	}
	if profiles == nil {
		profiles = []*model.CustomerRiskProfile{}
	}

	httputil.WriteJSON(w, http.StatusOK, dto.NewPageResponse(profiles, page, size, total))
}

// GetRiskProfile handles GET /api/v1/fraud/risk-profiles/{customerId}
func (h *Handler) GetRiskProfile(w http.ResponseWriter, r *http.Request) {
	customerID := chi.URLParam(r, "customerId")
	tenantID := auth.TenantIDOrDefault(r.Context())

	profile, err := h.svc.GetRiskProfile(r.Context(), tenantID, customerID)
	if err != nil {
		h.logger.Error("Failed to get risk profile", zap.Error(err))
		httputil.WriteInternalError(w, "Failed to get risk profile", r.URL.Path)
		return
	}
	if profile == nil {
		httputil.WriteNotFound(w, "Risk profile not found for customer: "+customerID, r.URL.Path)
		return
	}

	httputil.WriteJSON(w, http.StatusOK, profile)
}

// ListNetworkLinks handles GET /api/v1/fraud/network/{customerId}
func (h *Handler) ListNetworkLinks(w http.ResponseWriter, r *http.Request) {
	customerID := chi.URLParam(r, "customerId")
	tenantID := auth.TenantIDOrDefault(r.Context())

	links, err := h.svc.ListNetworkLinks(r.Context(), tenantID, customerID)
	if err != nil {
		h.logger.Error("Failed to list network links", zap.Error(err))
		httputil.WriteInternalError(w, "Failed to list network links", r.URL.Path)
		return
	}
	if links == nil {
		links = []*model.NetworkLink{}
	}

	httputil.WriteJSON(w, http.StatusOK, links)
}

// BulkAssignAlerts handles POST /api/v1/fraud/alerts/bulk/assign
func (h *Handler) BulkAssignAlerts(w http.ResponseWriter, r *http.Request) {
	var req model.BulkAlertActionRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		httputil.WriteBadRequest(w, "Invalid request body", r.URL.Path)
		return
	}

	count, err := h.svc.BulkAssignAlerts(r.Context(), req)
	if err != nil {
		h.logger.Error("Failed to bulk assign alerts", zap.Error(err))
		httputil.WriteInternalError(w, "Failed to bulk assign alerts", r.URL.Path)
		return
	}

	httputil.WriteJSON(w, http.StatusOK, map[string]interface{}{"assigned": count})
}

// BulkResolveAlerts handles POST /api/v1/fraud/alerts/bulk/resolve
func (h *Handler) BulkResolveAlerts(w http.ResponseWriter, r *http.Request) {
	var req model.BulkAlertActionRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		httputil.WriteBadRequest(w, "Invalid request body", r.URL.Path)
		return
	}

	count, err := h.svc.BulkResolveAlerts(r.Context(), req)
	if err != nil {
		h.logger.Error("Failed to bulk resolve alerts", zap.Error(err))
		httputil.WriteInternalError(w, "Failed to bulk resolve alerts", r.URL.Path)
		return
	}

	httputil.WriteJSON(w, http.StatusOK, map[string]interface{}{"resolved": count})
}

// CreateCase handles POST /api/v1/fraud/cases
func (h *Handler) CreateCase(w http.ResponseWriter, r *http.Request) {
	var req model.CreateCaseRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		httputil.WriteBadRequest(w, "Invalid request body", r.URL.Path)
		return
	}

	tenantID := auth.TenantIDOrDefault(r.Context())
	fraudCase, err := h.svc.CreateCase(r.Context(), req, tenantID)
	if err != nil {
		h.logger.Error("Failed to create case", zap.Error(err))
		httputil.WriteInternalError(w, "Failed to create case", r.URL.Path)
		return
	}

	httputil.WriteJSON(w, http.StatusCreated, fraudCase)
}

// UpdateCase handles PUT /api/v1/fraud/cases/{id}
func (h *Handler) UpdateCase(w http.ResponseWriter, r *http.Request) {
	id, err := uuid.Parse(chi.URLParam(r, "id"))
	if err != nil {
		httputil.WriteBadRequest(w, "Invalid case ID", r.URL.Path)
		return
	}

	var req model.UpdateCaseRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		httputil.WriteBadRequest(w, "Invalid request body", r.URL.Path)
		return
	}

	fraudCase, err := h.svc.UpdateCase(r.Context(), id, req)
	if err != nil {
		h.logger.Error("Failed to update case", zap.Error(err))
		httputil.WriteInternalError(w, "Failed to update case", r.URL.Path)
		return
	}
	if fraudCase == nil {
		httputil.WriteNotFound(w, "Case not found: "+id.String(), r.URL.Path)
		return
	}

	httputil.WriteJSON(w, http.StatusOK, fraudCase)
}

// GetCaseTimeline handles GET /api/v1/fraud/cases/{id}/timeline
func (h *Handler) GetCaseTimeline(w http.ResponseWriter, r *http.Request) {
	id, err := uuid.Parse(chi.URLParam(r, "id"))
	if err != nil {
		httputil.WriteBadRequest(w, "Invalid case ID", r.URL.Path)
		return
	}

	tenantID := auth.TenantIDOrDefault(r.Context())
	timeline, err := h.svc.GetCaseTimeline(r.Context(), id, tenantID)
	if err != nil {
		h.logger.Error("Failed to get case timeline", zap.Error(err))
		httputil.WriteInternalError(w, "Failed to get case timeline", r.URL.Path)
		return
	}
	if timeline == nil {
		httputil.WriteNotFound(w, "Case not found: "+id.String(), r.URL.Path)
		return
	}

	httputil.WriteJSON(w, http.StatusOK, timeline)
}

// DeactivateWatchlistEntry handles PUT /api/v1/fraud/watchlist/{id}/deactivate
func (h *Handler) DeactivateWatchlistEntry(w http.ResponseWriter, r *http.Request) {
	id, err := uuid.Parse(chi.URLParam(r, "id"))
	if err != nil {
		httputil.WriteBadRequest(w, "Invalid watchlist entry ID", r.URL.Path)
		return
	}

	entry, err := h.svc.DeactivateWatchlistEntry(r.Context(), id)
	if err != nil {
		h.logger.Error("Failed to deactivate watchlist entry", zap.Error(err))
		httputil.WriteInternalError(w, "Failed to deactivate watchlist entry", r.URL.Path)
		return
	}
	if entry == nil {
		httputil.WriteNotFound(w, "Watchlist entry not found: "+id.String(), r.URL.Path)
		return
	}

	httputil.WriteJSON(w, http.StatusOK, entry)
}

// ScreenCustomer handles POST /api/v1/fraud/watchlist/screen
func (h *Handler) ScreenCustomer(w http.ResponseWriter, r *http.Request) {
	var req model.ScreenCustomerRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		httputil.WriteBadRequest(w, "Invalid request body", r.URL.Path)
		return
	}

	tenantID := auth.TenantIDOrDefault(r.Context())
	matches, err := h.svc.ScreenCustomer(r.Context(), tenantID, req)
	if err != nil {
		h.logger.Error("Failed to screen customer", zap.Error(err))
		httputil.WriteInternalError(w, "Failed to screen customer", r.URL.Path)
		return
	}

	httputil.WriteJSON(w, http.StatusOK, map[string]interface{}{
		"matches":    matches,
		"matchCount": len(matches),
	})
}

// ListRecentEvents handles GET /api/v1/fraud/events/recent
func (h *Handler) ListRecentEvents(w http.ResponseWriter, r *http.Request) {
	tenantID := auth.TenantIDOrDefault(r.Context())

	page, _ := strconv.Atoi(r.URL.Query().Get("page"))
	size, _ := strconv.Atoi(r.URL.Query().Get("size"))
	if size <= 0 {
		size = 20
	}

	events, total, err := h.svc.ListRecentEvents(r.Context(), tenantID, page, size)
	if err != nil {
		h.logger.Error("Failed to list recent events", zap.Error(err))
		httputil.WriteInternalError(w, "Failed to list recent events", r.URL.Path)
		return
	}
	if events == nil {
		events = []*model.FraudEvent{}
	}

	httputil.WriteJSON(w, http.StatusOK, dto.NewPageResponse(events, page, size, total))
}

// ListSarReports handles GET /api/v1/fraud/sar-reports
func (h *Handler) ListSarReports(w http.ResponseWriter, r *http.Request) {
	tenantID := auth.TenantIDOrDefault(r.Context())

	page, _ := strconv.Atoi(r.URL.Query().Get("page"))
	size, _ := strconv.Atoi(r.URL.Query().Get("size"))
	if size <= 0 {
		size = 20
	}

	var status *model.SarStatus
	if s := r.URL.Query().Get("status"); s != "" {
		st := model.SarStatus(s)
		status = &st
	}

	var reportType *model.SarReportType
	if rt := r.URL.Query().Get("reportType"); rt != "" {
		t := model.SarReportType(rt)
		reportType = &t
	}

	reports, total, err := h.svc.ListSarReports(r.Context(), tenantID, status, reportType, page, size)
	if err != nil {
		h.logger.Error("Failed to list SAR reports", zap.Error(err))
		httputil.WriteInternalError(w, "Failed to list SAR reports", r.URL.Path)
		return
	}
	if reports == nil {
		reports = []*model.SarReport{}
	}

	httputil.WriteJSON(w, http.StatusOK, dto.NewPageResponse(reports, page, size, total))
}

// CreateSarReport handles POST /api/v1/fraud/sar-reports
func (h *Handler) CreateSarReport(w http.ResponseWriter, r *http.Request) {
	var req model.CreateSarReportRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		httputil.WriteBadRequest(w, "Invalid request body", r.URL.Path)
		return
	}

	tenantID := auth.TenantIDOrDefault(r.Context())
	report, err := h.svc.CreateSarReport(r.Context(), req, tenantID)
	if err != nil {
		h.logger.Error("Failed to create SAR report", zap.Error(err))
		httputil.WriteInternalError(w, "Failed to create SAR report", r.URL.Path)
		return
	}

	httputil.WriteJSON(w, http.StatusCreated, report)
}

// GetSarReport handles GET /api/v1/fraud/sar-reports/{id}
func (h *Handler) GetSarReport(w http.ResponseWriter, r *http.Request) {
	id, err := uuid.Parse(chi.URLParam(r, "id"))
	if err != nil {
		httputil.WriteBadRequest(w, "Invalid SAR report ID", r.URL.Path)
		return
	}

	report, err := h.svc.GetSarReport(r.Context(), id)
	if err != nil {
		h.logger.Error("Failed to get SAR report", zap.Error(err))
		httputil.WriteInternalError(w, "Failed to get SAR report", r.URL.Path)
		return
	}
	if report == nil {
		httputil.WriteNotFound(w, "SAR report not found: "+id.String(), r.URL.Path)
		return
	}

	httputil.WriteJSON(w, http.StatusOK, report)
}

// UpdateSarReport handles PUT /api/v1/fraud/sar-reports/{id}
func (h *Handler) UpdateSarReport(w http.ResponseWriter, r *http.Request) {
	id, err := uuid.Parse(chi.URLParam(r, "id"))
	if err != nil {
		httputil.WriteBadRequest(w, "Invalid SAR report ID", r.URL.Path)
		return
	}

	var req model.UpdateSarReportRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		httputil.WriteBadRequest(w, "Invalid request body", r.URL.Path)
		return
	}

	report, err := h.svc.UpdateSarReport(r.Context(), id, req)
	if err != nil {
		h.logger.Error("Failed to update SAR report", zap.Error(err))
		httputil.WriteInternalError(w, "Failed to update SAR report", r.URL.Path)
		return
	}
	if report == nil {
		httputil.WriteNotFound(w, "SAR report not found: "+id.String(), r.URL.Path)
		return
	}

	httputil.WriteJSON(w, http.StatusOK, report)
}

// EvaluateTransaction handles POST /api/v1/fraud/evaluate
func (h *Handler) EvaluateTransaction(w http.ResponseWriter, r *http.Request) {
	var req model.EvaluateTransactionRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		httputil.WriteBadRequest(w, "Invalid request body", r.URL.Path)
		return
	}

	tenantID := auth.TenantIDOrDefault(r.Context())
	resp, err := h.eng.Evaluate(r.Context(), tenantID, req)
	if err != nil {
		h.logger.Error("Failed to evaluate transaction", zap.Error(err))
		httputil.WriteInternalError(w, "Failed to evaluate transaction", r.URL.Path)
		return
	}

	httputil.WriteJSON(w, http.StatusOK, resp)
}

// ListAuditLog handles GET /api/v1/fraud/audit
func (h *Handler) ListAuditLog(w http.ResponseWriter, r *http.Request) {
	tenantID := auth.TenantIDOrDefault(r.Context())

	page, _ := strconv.Atoi(r.URL.Query().Get("page"))
	size, _ := strconv.Atoi(r.URL.Query().Get("size"))
	if size <= 0 {
		size = 20
	}

	var entityType *string
	if et := r.URL.Query().Get("entityType"); et != "" {
		entityType = &et
	}

	var entityID *uuid.UUID
	if eid := r.URL.Query().Get("entityId"); eid != "" {
		parsed, err := uuid.Parse(eid)
		if err == nil {
			entityID = &parsed
		}
	}

	resp, err := h.svc.ListAuditLog(r.Context(), tenantID, entityType, entityID, page, size)
	if err != nil {
		h.logger.Error("Failed to list audit log", zap.Error(err))
		httputil.WriteInternalError(w, "Failed to list audit log", r.URL.Path)
		return
	}

	httputil.WriteJSON(w, http.StatusOK, resp)
}
