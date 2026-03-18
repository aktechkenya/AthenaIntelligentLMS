package handler

import (
	"net/http"
	"strconv"

	"github.com/go-chi/chi/v5"
	"github.com/google/uuid"
	"go.uber.org/zap"

	"github.com/athena-lms/go-services/internal/common/auth"
	"github.com/athena-lms/go-services/internal/common/httputil"
	"github.com/athena-lms/go-services/internal/media/model"
	"github.com/athena-lms/go-services/internal/media/service"
)

// Handler handles HTTP requests for the media service.
type Handler struct {
	svc    *service.Service
	logger *zap.Logger
}

// New creates a new media Handler.
func New(svc *service.Service, logger *zap.Logger) *Handler {
	return &Handler{svc: svc, logger: logger}
}

// RegisterRoutes registers all media routes on the given chi.Router.
// All routes are under /api/v1/media.
func (h *Handler) RegisterRoutes(r chi.Router) {
	r.Route("/api/v1/media", func(r chi.Router) {
		r.Post("/upload/{customerId}", h.UploadForCustomer)
		r.Post("/upload", h.Upload)
		r.Get("/customer/{customerId}", h.GetCustomerMedia)
		r.Get("/download/{mediaId}", h.DownloadMedia)
		r.Get("/metadata/{mediaId}", h.GetMetadata)
		r.Get("/reference/{referenceId}", h.GetByReference)
		r.Get("/category/{category}", h.GetByCategory)
		r.Get("/", h.Search)
		r.Patch("/{mediaId}", h.UpdateMetadata)
		r.Delete("/{mediaId}", h.DeleteMedia)

		// Stats sub-routes
		r.Get("/stats", h.GetStats)
		r.Get("/stats/all", h.GetAllMedia)
	})
}

// UploadForCustomer handles POST /api/v1/media/upload/{customerId}
func (h *Handler) UploadForCustomer(w http.ResponseWriter, r *http.Request) {
	tenantID := auth.TenantIDOrDefault(r.Context())
	customerID := chi.URLParam(r, "customerId")

	if err := r.ParseMultipartForm(10 << 20); err != nil { // 10 MB limit
		httputil.WriteBadRequest(w, "Missing or invalid multipart form data", r.URL.Path)
		return
	}

	file, header, err := r.FormFile("file")
	if err != nil {
		httputil.WriteBadRequest(w, "Missing or invalid file parameter", r.URL.Path)
		return
	}
	defer file.Close()

	mediaType := r.FormValue("mediaType")
	if mediaType == "" {
		mediaType = "OTHER"
	}
	category := r.FormValue("category")
	if category == "" {
		category = "CUSTOMER_DOCUMENT"
	}
	description := formValuePtr(r, "description")

	currentUser := auth.UserIDFromContext(r.Context())
	if currentUser == "" {
		currentUser = "system"
	}

	params := service.UploadParams{
		TenantID:    tenantID,
		CustomerID:  &customerID,
		Category:    model.MediaCategory(category),
		MediaType:   model.MediaType(mediaType),
		Description: description,
		IsPublic:    false,
		UploadedBy:  currentUser,
		Filename:    header.Filename,
		ContentType: header.Header.Get("Content-Type"),
		FileSize:    header.Size,
		FileReader:  file,
	}

	media, err := h.svc.Upload(r.Context(), params)
	if err != nil {
		h.handleError(w, r, err)
		return
	}
	httputil.WriteJSON(w, http.StatusOK, media)
}

// Upload handles POST /api/v1/media/upload
func (h *Handler) Upload(w http.ResponseWriter, r *http.Request) {
	tenantID := auth.TenantIDOrDefault(r.Context())

	if err := r.ParseMultipartForm(10 << 20); err != nil {
		httputil.WriteBadRequest(w, "Missing or invalid multipart form data", r.URL.Path)
		return
	}

	file, header, err := r.FormFile("file")
	if err != nil {
		httputil.WriteBadRequest(w, "Missing or invalid file parameter", r.URL.Path)
		return
	}
	defer file.Close()

	category := r.FormValue("category")
	if category == "" {
		httputil.WriteBadRequest(w, "category is required", r.URL.Path)
		return
	}

	mediaType := r.FormValue("mediaType")
	if mediaType == "" {
		mediaType = "OTHER"
	}

	var referenceID *uuid.UUID
	if refStr := r.FormValue("referenceId"); refStr != "" {
		parsed, err := uuid.Parse(refStr)
		if err != nil {
			httputil.WriteBadRequest(w, "Invalid referenceId format", r.URL.Path)
			return
		}
		referenceID = &parsed
	}

	isPublic := false
	if r.FormValue("isPublic") == "true" {
		isPublic = true
	}

	currentUser := auth.UserIDFromContext(r.Context())
	if currentUser == "" {
		currentUser = "system"
	}

	params := service.UploadParams{
		TenantID:    tenantID,
		ReferenceID: referenceID,
		Category:    model.MediaCategory(category),
		MediaType:   model.MediaType(mediaType),
		Description: formValuePtr(r, "description"),
		Tags:        formValuePtr(r, "tags"),
		IsPublic:    isPublic,
		UploadedBy:  currentUser,
		ServiceName: formValuePtr(r, "serviceName"),
		Channel:     formValuePtr(r, "channel"),
		Filename:    header.Filename,
		ContentType: header.Header.Get("Content-Type"),
		FileSize:    header.Size,
		FileReader:  file,
	}

	media, err := h.svc.Upload(r.Context(), params)
	if err != nil {
		h.handleError(w, r, err)
		return
	}
	httputil.WriteJSON(w, http.StatusOK, media)
}

// GetCustomerMedia handles GET /api/v1/media/customer/{customerId}
func (h *Handler) GetCustomerMedia(w http.ResponseWriter, r *http.Request) {
	tenantID := auth.TenantIDOrDefault(r.Context())
	customerID := chi.URLParam(r, "customerId")

	media, err := h.svc.GetByCustomer(r.Context(), tenantID, customerID)
	if err != nil {
		h.handleError(w, r, err)
		return
	}
	if media == nil {
		media = []model.Media{}
	}
	httputil.WriteJSON(w, http.StatusOK, media)
}

// DownloadMedia handles GET /api/v1/media/download/{mediaId}
func (h *Handler) DownloadMedia(w http.ResponseWriter, r *http.Request) {
	tenantID := auth.TenantIDOrDefault(r.Context())
	mediaID, err := uuid.Parse(chi.URLParam(r, "mediaId"))
	if err != nil {
		httputil.WriteBadRequest(w, "Invalid media ID format", r.URL.Path)
		return
	}

	result, err := h.svc.Download(r.Context(), tenantID, mediaID)
	if err != nil {
		h.handleError(w, r, err)
		return
	}

	w.Header().Set("Content-Type", result.ContentType)
	w.Header().Set("Content-Disposition", `attachment; filename="`+result.OriginalFilename+`"`)
	http.ServeFile(w, r, result.FilePath)
}

// GetMetadata handles GET /api/v1/media/metadata/{mediaId}
func (h *Handler) GetMetadata(w http.ResponseWriter, r *http.Request) {
	tenantID := auth.TenantIDOrDefault(r.Context())
	mediaID, err := uuid.Parse(chi.URLParam(r, "mediaId"))
	if err != nil {
		httputil.WriteBadRequest(w, "Invalid media ID format", r.URL.Path)
		return
	}

	media, err := h.svc.GetMetadata(r.Context(), tenantID, mediaID)
	if err != nil {
		h.handleError(w, r, err)
		return
	}
	httputil.WriteJSON(w, http.StatusOK, media)
}

// GetByReference handles GET /api/v1/media/reference/{referenceId}
func (h *Handler) GetByReference(w http.ResponseWriter, r *http.Request) {
	tenantID := auth.TenantIDOrDefault(r.Context())
	referenceID, err := uuid.Parse(chi.URLParam(r, "referenceId"))
	if err != nil {
		httputil.WriteBadRequest(w, "Invalid reference ID format", r.URL.Path)
		return
	}

	media, err := h.svc.FindByReferenceID(r.Context(), tenantID, referenceID)
	if err != nil {
		h.handleError(w, r, err)
		return
	}
	if media == nil {
		media = []model.Media{}
	}
	httputil.WriteJSON(w, http.StatusOK, media)
}

// GetByCategory handles GET /api/v1/media/category/{category}
func (h *Handler) GetByCategory(w http.ResponseWriter, r *http.Request) {
	tenantID := auth.TenantIDOrDefault(r.Context())
	category := model.MediaCategory(chi.URLParam(r, "category"))

	media, err := h.svc.FindByCategory(r.Context(), tenantID, category)
	if err != nil {
		h.handleError(w, r, err)
		return
	}
	if media == nil {
		media = []model.Media{}
	}
	httputil.WriteJSON(w, http.StatusOK, media)
}

// Search handles GET /api/v1/media/
func (h *Handler) Search(w http.ResponseWriter, r *http.Request) {
	tenantID := auth.TenantIDOrDefault(r.Context())

	tag := r.URL.Query().Get("tag")
	if tag != "" {
		media, err := h.svc.FindByTag(r.Context(), tenantID, tag)
		if err != nil {
			h.handleError(w, r, err)
			return
		}
		if media == nil {
			media = []model.Media{}
		}
		httputil.WriteJSON(w, http.StatusOK, media)
		return
	}

	var category *model.MediaCategory
	if catStr := r.URL.Query().Get("category"); catStr != "" {
		cat := model.MediaCategory(catStr)
		category = &cat
	}
	var status *model.MediaStatus
	if statusStr := r.URL.Query().Get("status"); statusStr != "" {
		st := model.MediaStatus(statusStr)
		status = &st
	}

	media, err := h.svc.SearchMedia(r.Context(), tenantID, category, status)
	if err != nil {
		h.handleError(w, r, err)
		return
	}
	if media == nil {
		media = []model.Media{}
	}
	httputil.WriteJSON(w, http.StatusOK, media)
}

// UpdateMetadata handles PATCH /api/v1/media/{mediaId}
func (h *Handler) UpdateMetadata(w http.ResponseWriter, r *http.Request) {
	tenantID := auth.TenantIDOrDefault(r.Context())
	mediaID, err := uuid.Parse(chi.URLParam(r, "mediaId"))
	if err != nil {
		httputil.WriteBadRequest(w, "Invalid media ID format", r.URL.Path)
		return
	}

	// Support both form params (matching Java) and query params
	description := queryOrForm(r, "description")
	tags := queryOrForm(r, "tags")
	var status *model.MediaStatus
	if statusStr := queryOrFormValue(r, "status"); statusStr != "" {
		st := model.MediaStatus(statusStr)
		status = &st
	}

	media, err := h.svc.UpdateMetadata(r.Context(), tenantID, mediaID, description, tags, status)
	if err != nil {
		h.handleError(w, r, err)
		return
	}
	httputil.WriteJSON(w, http.StatusOK, media)
}

// DeleteMedia handles DELETE /api/v1/media/{mediaId}
func (h *Handler) DeleteMedia(w http.ResponseWriter, r *http.Request) {
	tenantID := auth.TenantIDOrDefault(r.Context())
	mediaID, err := uuid.Parse(chi.URLParam(r, "mediaId"))
	if err != nil {
		httputil.WriteBadRequest(w, "Invalid media ID format", r.URL.Path)
		return
	}

	if err := h.svc.Delete(r.Context(), tenantID, mediaID); err != nil {
		h.handleError(w, r, err)
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

// GetStats handles GET /api/v1/media/stats
func (h *Handler) GetStats(w http.ResponseWriter, r *http.Request) {
	tenantID := auth.TenantIDOrDefault(r.Context())

	stats, err := h.svc.GetStats(r.Context(), tenantID)
	if err != nil {
		h.handleError(w, r, err)
		return
	}
	httputil.WriteJSON(w, http.StatusOK, stats)
}

// GetAllMedia handles GET /api/v1/media/stats/all
func (h *Handler) GetAllMedia(w http.ResponseWriter, r *http.Request) {
	tenantID := auth.TenantIDOrDefault(r.Context())

	page, _ := strconv.Atoi(r.URL.Query().Get("page"))
	size, _ := strconv.Atoi(r.URL.Query().Get("size"))
	if size <= 0 {
		size = 20
	}
	if page < 0 {
		page = 0
	}

	result, err := h.svc.GetAllPaginated(r.Context(), tenantID, page, size)
	if err != nil {
		h.handleError(w, r, err)
		return
	}
	httputil.WriteJSON(w, http.StatusOK, result)
}

// handleError writes an appropriate error response based on the error message.
func (h *Handler) handleError(w http.ResponseWriter, r *http.Request, err error) {
	msg := err.Error()
	if containsNotFound(msg) {
		httputil.WriteNotFound(w, msg, r.URL.Path)
		return
	}
	if containsEmpty(msg) || containsOutside(msg) {
		httputil.WriteBadRequest(w, msg, r.URL.Path)
		return
	}
	h.logger.Error("Internal error", zap.Error(err), zap.String("path", r.URL.Path))
	httputil.WriteInternalError(w, msg, r.URL.Path)
}

func containsNotFound(msg string) bool {
	return len(msg) >= 9 && (msg == "media not found" ||
		(len(msg) > 15 && msg[:15] == "media not found") ||
		contains(msg, "not found"))
}

func containsEmpty(msg string) bool {
	return contains(msg, "empty file")
}

func containsOutside(msg string) bool {
	return contains(msg, "outside storage")
}

func contains(s, substr string) bool {
	return len(s) >= len(substr) && searchString(s, substr)
}

func searchString(s, substr string) bool {
	for i := 0; i <= len(s)-len(substr); i++ {
		if s[i:i+len(substr)] == substr {
			return true
		}
	}
	return false
}

func formValuePtr(r *http.Request, key string) *string {
	v := r.FormValue(key)
	if v == "" {
		return nil
	}
	return &v
}

func queryOrForm(r *http.Request, key string) *string {
	v := r.URL.Query().Get(key)
	if v == "" {
		v = r.FormValue(key)
	}
	if v == "" {
		return nil
	}
	return &v
}

func queryOrFormValue(r *http.Request, key string) string {
	v := r.URL.Query().Get(key)
	if v == "" {
		v = r.FormValue(key)
	}
	return v
}
