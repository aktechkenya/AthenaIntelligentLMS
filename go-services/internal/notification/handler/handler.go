package handler

import (
	"encoding/json"
	"net/http"
	"strconv"
	"strings"

	"github.com/go-chi/chi/v5"
	"go.uber.org/zap"

	"github.com/athena-lms/go-services/internal/common/dto"
	"github.com/athena-lms/go-services/internal/common/httputil"
	"github.com/athena-lms/go-services/internal/notification/model"
	"github.com/athena-lms/go-services/internal/notification/service"
)

// Handler exposes notification HTTP endpoints.
type Handler struct {
	svc    *service.Service
	logger *zap.Logger
}

// New creates a new Handler.
func New(svc *service.Service, logger *zap.Logger) *Handler {
	return &Handler{svc: svc, logger: logger}
}

// RegisterRoutes mounts all notification routes on the given router.
func (h *Handler) RegisterRoutes(r chi.Router) {
	r.Route("/api/v1/notifications", func(r chi.Router) {
		r.Get("/logs", h.getLogs)
		r.Get("/config/{type}", h.getConfig)
		r.Get("/email-config", h.getEmailConfig)
		r.Get("/sms-config", h.getSMSConfig)
		r.Post("/config", h.updateConfig)
		r.Post("/send", h.send)
	})
}

// getLogs returns paginated notification logs, newest first.
func (h *Handler) getLogs(w http.ResponseWriter, r *http.Request) {
	page, _ := strconv.Atoi(r.URL.Query().Get("page"))
	size, _ := strconv.Atoi(r.URL.Query().Get("size"))
	if size <= 0 {
		size = 20
	}

	logs, total, err := h.svc.ListLogs(r.Context(), page, size)
	if err != nil {
		h.logger.Error("Failed to list logs", zap.Error(err))
		httputil.WriteInternalError(w, "Failed to list notification logs", r.URL.Path)
		return
	}
	if logs == nil {
		logs = []model.NotificationLog{}
	}

	httputil.WriteJSON(w, http.StatusOK, dto.NewPageResponse(logs, page, size, total))
}

// getConfig returns the notification config for EMAIL or SMS.
func (h *Handler) getConfig(w http.ResponseWriter, r *http.Request) {
	configType := strings.ToUpper(chi.URLParam(r, "type"))
	h.respondWithConfig(w, r, configType)
}

// getEmailConfig returns the EMAIL notification config.
func (h *Handler) getEmailConfig(w http.ResponseWriter, r *http.Request) {
	h.respondWithConfig(w, r, "EMAIL")
}

// getSMSConfig returns the SMS notification config.
func (h *Handler) getSMSConfig(w http.ResponseWriter, r *http.Request) {
	h.respondWithConfig(w, r, "SMS")
}

// respondWithConfig fetches and returns the config for the given type.
func (h *Handler) respondWithConfig(w http.ResponseWriter, r *http.Request, configType string) {
	config, err := h.svc.GetConfig(r.Context(), configType)
	if err != nil {
		h.logger.Error("Failed to get config", zap.Error(err))
		httputil.WriteInternalError(w, "Failed to get notification config", r.URL.Path)
		return
	}
	if config == nil {
		httputil.WriteNotFound(w, "Config not found for type: "+configType, r.URL.Path)
		return
	}
	httputil.WriteJSON(w, http.StatusOK, config)
}

// updateConfig creates or updates a notification config.
func (h *Handler) updateConfig(w http.ResponseWriter, r *http.Request) {
	var config model.NotificationConfig
	if err := json.NewDecoder(r.Body).Decode(&config); err != nil {
		httputil.WriteBadRequest(w, "Invalid request body", r.URL.Path)
		return
	}

	result, err := h.svc.UpdateConfig(r.Context(), &config)
	if err != nil {
		h.logger.Error("Failed to update config", zap.Error(err))
		httputil.WriteInternalError(w, "Failed to update notification config", r.URL.Path)
		return
	}
	httputil.WriteJSON(w, http.StatusOK, result)
}

// send manually sends a notification.
func (h *Handler) send(w http.ResponseWriter, r *http.Request) {
	var req model.NotificationRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		httputil.WriteBadRequest(w, "Invalid request body", r.URL.Path)
		return
	}

	switch strings.ToUpper(req.Type) {
	case "EMAIL":
		svcName := req.ServiceName
		if svcName == "" {
			svcName = "api"
		}
		if err := h.svc.SendEmail(r.Context(), svcName, req.Recipient, req.Subject, req.Message); err != nil {
			// sendEmail logs SKIPPED internally and returns nil, so an error here is a real failure
			h.logger.Error("Failed to send email", zap.Error(err))
			httputil.WriteInternalError(w, "Failed to send email: "+err.Error(), r.URL.Path)
			return
		}
		httputil.WriteJSON(w, http.StatusOK, map[string]string{"message": "Email queued"})
	case "SMS":
		httputil.WriteJSON(w, http.StatusOK, map[string]string{"message": "SMS not yet implemented"})
	default:
		httputil.WriteBadRequest(w, "Unsupported notification type", r.URL.Path)
	}
}
