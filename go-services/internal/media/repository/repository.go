package repository

import (
	"context"
	"fmt"
	"strings"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"

	"github.com/athena-lms/go-services/internal/media/model"
)

// Repository provides data access for media files.
type Repository struct {
	pool *pgxpool.Pool
}

// New creates a new media Repository.
func New(pool *pgxpool.Pool) *Repository {
	return &Repository{pool: pool}
}

// allColumns is the list of columns for scanning media rows.
const allColumns = `id, tenant_id, reference_id, customer_id, category, media_type,
	original_filename, stored_filename, content_type, file_size,
	uploaded_by, service_name, channel, tags, description, is_public,
	thumbnail, status, created_at`

// scanMedia scans a single media row from a pgx.Row.
func scanMedia(row pgx.Row) (model.Media, error) {
	var m model.Media
	err := row.Scan(
		&m.ID, &m.TenantID, &m.ReferenceID, &m.CustomerID, &m.Category, &m.MediaType,
		&m.OriginalFilename, &m.StoredFilename, &m.ContentType, &m.FileSize,
		&m.UploadedBy, &m.ServiceName, &m.Channel, &m.Tags, &m.Description, &m.IsPublic,
		&m.Thumbnail, &m.Status, &m.CreatedAt,
	)
	return m, err
}

// scanMediaRows scans multiple media rows from pgx.Rows.
func scanMediaRows(rows pgx.Rows) ([]model.Media, error) {
	var result []model.Media
	for rows.Next() {
		var m model.Media
		err := rows.Scan(
			&m.ID, &m.TenantID, &m.ReferenceID, &m.CustomerID, &m.Category, &m.MediaType,
			&m.OriginalFilename, &m.StoredFilename, &m.ContentType, &m.FileSize,
			&m.UploadedBy, &m.ServiceName, &m.Channel, &m.Tags, &m.Description, &m.IsPublic,
			&m.Thumbnail, &m.Status, &m.CreatedAt,
		)
		if err != nil {
			return nil, err
		}
		result = append(result, m)
	}
	return result, rows.Err()
}

// Save inserts a new media record and returns it with the generated ID and created_at.
func (r *Repository) Save(ctx context.Context, m *model.Media) (*model.Media, error) {
	query := `INSERT INTO media_files (
		tenant_id, reference_id, customer_id, category, media_type,
		original_filename, stored_filename, content_type, file_size,
		uploaded_by, service_name, channel, tags, description, is_public,
		thumbnail, status
	) VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14,$15,$16,$17)
	RETURNING ` + allColumns

	row := r.pool.QueryRow(ctx, query,
		m.TenantID, m.ReferenceID, m.CustomerID, m.Category, m.MediaType,
		m.OriginalFilename, m.StoredFilename, m.ContentType, m.FileSize,
		m.UploadedBy, m.ServiceName, m.Channel, m.Tags, m.Description, m.IsPublic,
		m.Thumbnail, m.Status,
	)
	saved, err := scanMedia(row)
	if err != nil {
		return nil, fmt.Errorf("save media: %w", err)
	}
	return &saved, nil
}

// FindByID returns a media record by ID and tenant.
func (r *Repository) FindByID(ctx context.Context, tenantID string, id uuid.UUID) (*model.Media, error) {
	query := `SELECT ` + allColumns + ` FROM media_files WHERE id = $1 AND tenant_id = $2`
	row := r.pool.QueryRow(ctx, query, id, tenantID)
	m, err := scanMedia(row)
	if err != nil {
		if err == pgx.ErrNoRows {
			return nil, nil
		}
		return nil, fmt.Errorf("find media by id: %w", err)
	}
	return &m, nil
}

// FindByCustomerID returns all media for a customer within a tenant.
func (r *Repository) FindByCustomerID(ctx context.Context, tenantID, customerID string) ([]model.Media, error) {
	query := `SELECT ` + allColumns + ` FROM media_files WHERE customer_id = $1 AND tenant_id = $2 ORDER BY created_at DESC`
	rows, err := r.pool.Query(ctx, query, customerID, tenantID)
	if err != nil {
		return nil, fmt.Errorf("find by customer: %w", err)
	}
	defer rows.Close()
	return scanMediaRows(rows)
}

// FindByCustomerIDAndMediaType returns media for a customer with a specific type.
func (r *Repository) FindByCustomerIDAndMediaType(ctx context.Context, tenantID, customerID string, mediaType model.MediaType) ([]model.Media, error) {
	query := `SELECT ` + allColumns + ` FROM media_files WHERE customer_id = $1 AND media_type = $2 AND tenant_id = $3 ORDER BY created_at DESC`
	rows, err := r.pool.Query(ctx, query, customerID, mediaType, tenantID)
	if err != nil {
		return nil, fmt.Errorf("find by customer and type: %w", err)
	}
	defer rows.Close()
	return scanMediaRows(rows)
}

// ExistsByCustomerIDAndMediaType checks existence of a media type for a customer.
func (r *Repository) ExistsByCustomerIDAndMediaType(ctx context.Context, tenantID, customerID string, mediaType model.MediaType) (bool, error) {
	query := `SELECT EXISTS(SELECT 1 FROM media_files WHERE customer_id = $1 AND media_type = $2 AND tenant_id = $3)`
	var exists bool
	err := r.pool.QueryRow(ctx, query, customerID, mediaType, tenantID).Scan(&exists)
	return exists, err
}

// FindByCategory returns all media for a category within a tenant.
func (r *Repository) FindByCategory(ctx context.Context, tenantID string, category model.MediaCategory) ([]model.Media, error) {
	query := `SELECT ` + allColumns + ` FROM media_files WHERE category = $1 AND tenant_id = $2 ORDER BY created_at DESC`
	rows, err := r.pool.Query(ctx, query, category, tenantID)
	if err != nil {
		return nil, fmt.Errorf("find by category: %w", err)
	}
	defer rows.Close()
	return scanMediaRows(rows)
}

// FindByReferenceID returns all media for a reference entity.
func (r *Repository) FindByReferenceID(ctx context.Context, tenantID string, referenceID uuid.UUID) ([]model.Media, error) {
	query := `SELECT ` + allColumns + ` FROM media_files WHERE reference_id = $1 AND tenant_id = $2 ORDER BY created_at DESC`
	rows, err := r.pool.Query(ctx, query, referenceID, tenantID)
	if err != nil {
		return nil, fmt.Errorf("find by reference: %w", err)
	}
	defer rows.Close()
	return scanMediaRows(rows)
}

// FindByCategoryAndReferenceID returns media for a given category and reference.
func (r *Repository) FindByCategoryAndReferenceID(ctx context.Context, tenantID string, category model.MediaCategory, referenceID uuid.UUID) ([]model.Media, error) {
	query := `SELECT ` + allColumns + ` FROM media_files WHERE category = $1 AND reference_id = $2 AND tenant_id = $3 ORDER BY created_at DESC`
	rows, err := r.pool.Query(ctx, query, category, referenceID, tenantID)
	if err != nil {
		return nil, fmt.Errorf("find by category and reference: %w", err)
	}
	defer rows.Close()
	return scanMediaRows(rows)
}

// FindByStatus returns all media with a given status.
func (r *Repository) FindByStatus(ctx context.Context, tenantID string, status model.MediaStatus) ([]model.Media, error) {
	query := `SELECT ` + allColumns + ` FROM media_files WHERE status = $1 AND tenant_id = $2 ORDER BY created_at DESC`
	rows, err := r.pool.Query(ctx, query, status, tenantID)
	if err != nil {
		return nil, fmt.Errorf("find by status: %w", err)
	}
	defer rows.Close()
	return scanMediaRows(rows)
}

// FindByTagsContaining returns media whose tags contain the given substring.
func (r *Repository) FindByTagsContaining(ctx context.Context, tenantID, tag string) ([]model.Media, error) {
	query := `SELECT ` + allColumns + ` FROM media_files WHERE tags LIKE '%' || $1 || '%' AND tenant_id = $2 ORDER BY created_at DESC`
	rows, err := r.pool.Query(ctx, query, tag, tenantID)
	if err != nil {
		return nil, fmt.Errorf("find by tag: %w", err)
	}
	defer rows.Close()
	return scanMediaRows(rows)
}

// SearchMedia searches media with optional filters.
func (r *Repository) SearchMedia(ctx context.Context, tenantID string, category *model.MediaCategory, status *model.MediaStatus) ([]model.Media, error) {
	var conditions []string
	var args []any
	argIdx := 1

	conditions = append(conditions, fmt.Sprintf("tenant_id = $%d", argIdx))
	args = append(args, tenantID)
	argIdx++

	if category != nil {
		conditions = append(conditions, fmt.Sprintf("category = $%d", argIdx))
		args = append(args, *category)
		argIdx++
	}
	if status != nil {
		conditions = append(conditions, fmt.Sprintf("status = $%d", argIdx))
		args = append(args, *status)
		argIdx++
	}

	query := `SELECT ` + allColumns + ` FROM media_files WHERE ` + strings.Join(conditions, " AND ") + ` ORDER BY created_at DESC`
	rows, err := r.pool.Query(ctx, query, args...)
	if err != nil {
		return nil, fmt.Errorf("search media: %w", err)
	}
	defer rows.Close()
	return scanMediaRows(rows)
}

// FindAllPaginated returns a page of media for a tenant.
func (r *Repository) FindAllPaginated(ctx context.Context, tenantID string, page, size int) ([]model.Media, int64, error) {
	countQuery := `SELECT COUNT(*) FROM media_files WHERE tenant_id = $1`
	var total int64
	if err := r.pool.QueryRow(ctx, countQuery, tenantID).Scan(&total); err != nil {
		return nil, 0, fmt.Errorf("count media: %w", err)
	}

	offset := page * size
	query := `SELECT ` + allColumns + ` FROM media_files WHERE tenant_id = $1 ORDER BY created_at DESC LIMIT $2 OFFSET $3`
	rows, err := r.pool.Query(ctx, query, tenantID, size, offset)
	if err != nil {
		return nil, 0, fmt.Errorf("find all paginated: %w", err)
	}
	defer rows.Close()
	items, err := scanMediaRows(rows)
	if err != nil {
		return nil, 0, err
	}
	return items, total, nil
}

// CountByMediaType returns a map of media type to count for a tenant.
func (r *Repository) CountByMediaType(ctx context.Context, tenantID string) (map[string]int64, error) {
	query := `SELECT media_type, COUNT(*) FROM media_files WHERE tenant_id = $1 GROUP BY media_type`
	rows, err := r.pool.Query(ctx, query, tenantID)
	if err != nil {
		return nil, fmt.Errorf("count by type: %w", err)
	}
	defer rows.Close()

	result := make(map[string]int64)
	for rows.Next() {
		var mediaType string
		var count int64
		if err := rows.Scan(&mediaType, &count); err != nil {
			return nil, err
		}
		result[mediaType] = count
	}
	return result, rows.Err()
}

// CountByCategory returns a map of category to count for a tenant.
func (r *Repository) CountByCategory(ctx context.Context, tenantID string) (map[string]int64, error) {
	query := `SELECT category, COUNT(*) FROM media_files WHERE tenant_id = $1 GROUP BY category`
	rows, err := r.pool.Query(ctx, query, tenantID)
	if err != nil {
		return nil, fmt.Errorf("count by category: %w", err)
	}
	defer rows.Close()

	result := make(map[string]int64)
	for rows.Next() {
		var category string
		var count int64
		if err := rows.Scan(&category, &count); err != nil {
			return nil, err
		}
		result[category] = count
	}
	return result, rows.Err()
}

// Count returns the total number of media records for a tenant.
func (r *Repository) Count(ctx context.Context, tenantID string) (int64, error) {
	var count int64
	err := r.pool.QueryRow(ctx, `SELECT COUNT(*) FROM media_files WHERE tenant_id = $1`, tenantID).Scan(&count)
	return count, err
}

// SumFileSize returns the total file size for all media in a tenant.
func (r *Repository) SumFileSize(ctx context.Context, tenantID string) (int64, error) {
	var sum int64
	err := r.pool.QueryRow(ctx, `SELECT COALESCE(SUM(file_size), 0) FROM media_files WHERE tenant_id = $1`, tenantID).Scan(&sum)
	return sum, err
}

// Update updates the mutable fields of a media record.
func (r *Repository) Update(ctx context.Context, m *model.Media) (*model.Media, error) {
	query := `UPDATE media_files SET
		description = $1, tags = $2, status = $3
		WHERE id = $4 AND tenant_id = $5
		RETURNING ` + allColumns
	row := r.pool.QueryRow(ctx, query, m.Description, m.Tags, m.Status, m.ID, m.TenantID)
	updated, err := scanMedia(row)
	if err != nil {
		return nil, fmt.Errorf("update media: %w", err)
	}
	return &updated, nil
}

// Delete removes a media record by ID and tenant.
func (r *Repository) Delete(ctx context.Context, tenantID string, id uuid.UUID) error {
	tag, err := r.pool.Exec(ctx, `DELETE FROM media_files WHERE id = $1 AND tenant_id = $2`, id, tenantID)
	if err != nil {
		return fmt.Errorf("delete media: %w", err)
	}
	if tag.RowsAffected() == 0 {
		return fmt.Errorf("media not found: %s", id)
	}
	return nil
}
