package service

import (
	"context"
	"fmt"
	"io"
	"math"
	"os"
	"path/filepath"
	"strings"

	"github.com/google/uuid"
	"go.uber.org/zap"

	"github.com/athena-lms/go-services/internal/media/model"
	"github.com/athena-lms/go-services/internal/media/repository"
)

// Service provides business logic for media operations.
type Service struct {
	repo            *repository.Repository
	storageLocation string
	logger          *zap.Logger
}

// New creates a new media Service and ensures the storage directory exists.
func New(repo *repository.Repository, storageLocation string, logger *zap.Logger) (*Service, error) {
	if storageLocation == "" {
		storageLocation = "/app/storage"
	}
	if err := os.MkdirAll(storageLocation, 0o755); err != nil {
		return nil, fmt.Errorf("could not initialize storage at %s: %w", storageLocation, err)
	}
	logger.Info("Storage initialized", zap.String("path", storageLocation))
	return &Service{
		repo:            repo,
		storageLocation: storageLocation,
		logger:          logger,
	}, nil
}

// UploadParams holds the parameters for uploading a media file.
type UploadParams struct {
	TenantID    string
	CustomerID  *string
	ReferenceID *uuid.UUID
	Category    model.MediaCategory
	MediaType   model.MediaType
	Description *string
	Tags        *string
	IsPublic    bool
	UploadedBy  string
	ServiceName *string
	Channel     *string

	// File information
	Filename    string
	ContentType string
	FileSize    int64
	FileReader  io.Reader
}

// Upload stores a file on disk and creates a database record.
func (s *Service) Upload(ctx context.Context, params UploadParams) (*model.Media, error) {
	if params.FileSize == 0 {
		return nil, fmt.Errorf("cannot upload empty file")
	}

	ext := getExtension(params.Filename)
	storedFilename := uuid.New().String() + ext

	destPath := filepath.Join(s.storageLocation, storedFilename)
	// Ensure path traversal protection
	absDir, _ := filepath.Abs(s.storageLocation)
	absDest, _ := filepath.Abs(destPath)
	if !strings.HasPrefix(absDest, absDir) {
		return nil, fmt.Errorf("cannot store file outside storage directory")
	}

	outFile, err := os.Create(destPath)
	if err != nil {
		return nil, fmt.Errorf("failed to create file %s: %w", params.Filename, err)
	}
	defer outFile.Close()

	if _, err := io.Copy(outFile, params.FileReader); err != nil {
		os.Remove(destPath)
		return nil, fmt.Errorf("failed to store file %s: %w", params.Filename, err)
	}

	media := &model.Media{
		TenantID:         params.TenantID,
		ReferenceID:      params.ReferenceID,
		CustomerID:       params.CustomerID,
		Category:         params.Category,
		MediaType:        params.MediaType,
		OriginalFilename: params.Filename,
		StoredFilename:   storedFilename,
		ContentType:      params.ContentType,
		FileSize:         &params.FileSize,
		Description:      params.Description,
		Tags:             params.Tags,
		IsPublic:         params.IsPublic,
		UploadedBy:       &params.UploadedBy,
		ServiceName:      params.ServiceName,
		Channel:          params.Channel,
		Status:           model.MediaStatusActive,
	}

	if s.repo == nil {
		os.Remove(destPath)
		return nil, fmt.Errorf("failed to save media record: repository not initialized")
	}

	saved, err := s.repo.Save(ctx, media)
	if err != nil {
		os.Remove(destPath)
		return nil, fmt.Errorf("failed to save media record: %w", err)
	}

	s.logger.Info("Media uploaded",
		zap.String("id", saved.ID.String()),
		zap.String("category", string(saved.Category)),
		zap.String("tenant", saved.TenantID),
	)
	return saved, nil
}

// FileResult holds the file content and metadata for a download.
type FileResult struct {
	FilePath        string
	ContentType     string
	OriginalFilename string
}

// Download returns the file path and metadata for downloading a media file.
func (s *Service) Download(ctx context.Context, tenantID string, mediaID uuid.UUID) (*FileResult, error) {
	media, err := s.repo.FindByID(ctx, tenantID, mediaID)
	if err != nil {
		return nil, err
	}
	if media == nil {
		return nil, fmt.Errorf("media not found: %s", mediaID)
	}

	filePath := filepath.Join(s.storageLocation, media.StoredFilename)
	if _, err := os.Stat(filePath); os.IsNotExist(err) {
		return nil, fmt.Errorf("could not read file: %s", media.OriginalFilename)
	}

	return &FileResult{
		FilePath:        filePath,
		ContentType:     media.ContentType,
		OriginalFilename: media.OriginalFilename,
	}, nil
}

// GetMetadata returns the metadata for a single media file.
func (s *Service) GetMetadata(ctx context.Context, tenantID string, mediaID uuid.UUID) (*model.Media, error) {
	media, err := s.repo.FindByID(ctx, tenantID, mediaID)
	if err != nil {
		return nil, err
	}
	if media == nil {
		return nil, fmt.Errorf("media not found: %s", mediaID)
	}
	return media, nil
}

// GetByCustomer returns all media for a customer.
func (s *Service) GetByCustomer(ctx context.Context, tenantID, customerID string) ([]model.Media, error) {
	return s.repo.FindByCustomerID(ctx, tenantID, customerID)
}

// FindByCategory returns all media for a category.
func (s *Service) FindByCategory(ctx context.Context, tenantID string, category model.MediaCategory) ([]model.Media, error) {
	return s.repo.FindByCategory(ctx, tenantID, category)
}

// FindByReferenceID returns all media for a reference entity.
func (s *Service) FindByReferenceID(ctx context.Context, tenantID string, referenceID uuid.UUID) ([]model.Media, error) {
	return s.repo.FindByReferenceID(ctx, tenantID, referenceID)
}

// FindByTag returns media whose tags contain the given substring.
func (s *Service) FindByTag(ctx context.Context, tenantID, tag string) ([]model.Media, error) {
	return s.repo.FindByTagsContaining(ctx, tenantID, tag)
}

// SearchMedia searches with optional category and status filters.
func (s *Service) SearchMedia(ctx context.Context, tenantID string, category *model.MediaCategory, status *model.MediaStatus) ([]model.Media, error) {
	return s.repo.SearchMedia(ctx, tenantID, category, status)
}

// UpdateMetadata updates the description, tags, and/or status of a media record.
func (s *Service) UpdateMetadata(ctx context.Context, tenantID string, mediaID uuid.UUID, description *string, tags *string, status *model.MediaStatus) (*model.Media, error) {
	media, err := s.repo.FindByID(ctx, tenantID, mediaID)
	if err != nil {
		return nil, err
	}
	if media == nil {
		return nil, fmt.Errorf("media not found: %s", mediaID)
	}

	if description != nil {
		media.Description = description
	}
	if tags != nil {
		media.Tags = tags
	}
	if status != nil {
		media.Status = *status
	}

	updated, err := s.repo.Update(ctx, media)
	if err != nil {
		return nil, err
	}

	s.logger.Info("Media metadata updated", zap.String("id", mediaID.String()))
	return updated, nil
}

// GetAllPaginated returns a paginated list of all media for a tenant.
func (s *Service) GetAllPaginated(ctx context.Context, tenantID string, page, size int) (*model.MediaPage, error) {
	items, total, err := s.repo.FindAllPaginated(ctx, tenantID, page, size)
	if err != nil {
		return nil, err
	}
	if items == nil {
		items = []model.Media{}
	}

	totalPages := 0
	if size > 0 {
		totalPages = int(math.Ceil(float64(total) / float64(size)))
	}

	return &model.MediaPage{
		Content:       items,
		TotalElements: total,
		TotalPages:    totalPages,
		Size:          size,
		Number:        page,
	}, nil
}

// GetStats returns storage and document statistics.
func (s *Service) GetStats(ctx context.Context, tenantID string) (*model.MediaStats, error) {
	var stat os.FileInfo
	var totalSpace, freeSpace int64

	// Get disk space info (best effort)
	stat, err := os.Stat(s.storageLocation)
	if err == nil && stat.IsDir() {
		// Use syscall-based approach for disk stats would be OS-specific,
		// so we provide placeholder values that can be enhanced later.
		// For now, report the used space from the database.
		totalSpace = 0
		freeSpace = 0
	}

	usedSpace, err := s.repo.SumFileSize(ctx, tenantID)
	if err != nil {
		return nil, err
	}

	totalDocs, err := s.repo.Count(ctx, tenantID)
	if err != nil {
		return nil, err
	}

	docsByType, err := s.repo.CountByMediaType(ctx, tenantID)
	if err != nil {
		return nil, err
	}

	usedPercentage := 0.0
	if totalSpace > 0 {
		usedPercentage = float64(usedSpace) * 100 / float64(totalSpace)
	}

	return &model.MediaStats{
		TotalSpace:      totalSpace,
		UsedSpace:       usedSpace,
		FreeSpace:       freeSpace,
		UsedPercentage:  usedPercentage,
		TotalDocuments:  totalDocs,
		DocumentsByType: docsByType,
	}, nil
}

// Delete removes a media file from disk and database.
func (s *Service) Delete(ctx context.Context, tenantID string, mediaID uuid.UUID) error {
	media, err := s.repo.FindByID(ctx, tenantID, mediaID)
	if err != nil {
		return err
	}
	if media == nil {
		return fmt.Errorf("media not found: %s", mediaID)
	}

	filePath := filepath.Join(s.storageLocation, media.StoredFilename)
	os.Remove(filePath) // best effort

	if err := s.repo.Delete(ctx, tenantID, mediaID); err != nil {
		return err
	}

	s.logger.Info("Media deleted", zap.String("id", mediaID.String()))
	return nil
}

func getExtension(filename string) string {
	if filename == "" || !strings.Contains(filename, ".") {
		return ""
	}
	return filename[strings.LastIndex(filename, "."):]
}
