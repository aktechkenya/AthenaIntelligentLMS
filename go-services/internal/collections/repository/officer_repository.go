package repository

import (
	"context"
	"fmt"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"

	"github.com/athena-lms/go-services/internal/collections/model"
)

// OfficerRepository provides data access for collection_officers.
type OfficerRepository struct {
	pool *pgxpool.Pool
}

// NewOfficerRepository creates a new OfficerRepository.
func NewOfficerRepository(pool *pgxpool.Pool) *OfficerRepository {
	return &OfficerRepository{pool: pool}
}

const officerColumns = `id, tenant_id, username, max_cases, is_active, created_at, updated_at`

// Save inserts or updates a collection officer.
func (r *OfficerRepository) Save(ctx context.Context, o *model.CollectionOfficer) (*model.CollectionOfficer, error) {
	now := time.Now().UTC()
	if o.ID == uuid.Nil {
		o.ID = uuid.New()
		o.CreatedAt = now
		o.UpdatedAt = now
		_, err := r.pool.Exec(ctx, `
			INSERT INTO collection_officers
				(id, tenant_id, username, max_cases, is_active, created_at, updated_at)
			VALUES ($1,$2,$3,$4,$5,$6,$7)`,
			o.ID, o.TenantID, o.Username, o.MaxCases, o.IsActive, o.CreatedAt, o.UpdatedAt,
		)
		if err != nil {
			return nil, fmt.Errorf("insert officer: %w", err)
		}
		return o, nil
	}

	o.UpdatedAt = now
	_, err := r.pool.Exec(ctx, `
		UPDATE collection_officers SET
			max_cases=$2, is_active=$3, updated_at=$4
		WHERE id=$1`,
		o.ID, o.MaxCases, o.IsActive, o.UpdatedAt,
	)
	if err != nil {
		return nil, fmt.Errorf("update officer: %w", err)
	}
	return o, nil
}

// FindByTenantID returns all officers for a tenant.
func (r *OfficerRepository) FindByTenantID(ctx context.Context, tenantID string) ([]*model.CollectionOfficer, error) {
	return r.scanMany(ctx, `
		SELECT `+officerColumns+`
		FROM collection_officers
		WHERE tenant_id=$1
		ORDER BY username ASC`, tenantID)
}

// FindByTenantIDAndID returns a single officer by tenant and ID.
func (r *OfficerRepository) FindByTenantIDAndID(ctx context.Context, tenantID string, id uuid.UUID) (*model.CollectionOfficer, error) {
	return r.scanOne(ctx, `
		SELECT `+officerColumns+`
		FROM collection_officers
		WHERE tenant_id=$1 AND id=$2`, tenantID, id)
}

// FindByTenantIDAndUsername returns an officer by tenant and username.
func (r *OfficerRepository) FindByTenantIDAndUsername(ctx context.Context, tenantID, username string) (*model.CollectionOfficer, error) {
	return r.scanOne(ctx, `
		SELECT `+officerColumns+`
		FROM collection_officers
		WHERE tenant_id=$1 AND username=$2`, tenantID, username)
}

// FindActiveByTenantID returns all active officers for a tenant.
func (r *OfficerRepository) FindActiveByTenantID(ctx context.Context, tenantID string) ([]*model.CollectionOfficer, error) {
	return r.scanMany(ctx, `
		SELECT `+officerColumns+`
		FROM collection_officers
		WHERE tenant_id=$1 AND is_active=true
		ORDER BY username ASC`, tenantID)
}

// GetWorkload returns per-officer case counts grouped by stage.
func (r *OfficerRepository) GetWorkload(ctx context.Context, tenantID string) ([]model.OfficerWorkload, error) {
	rows, err := r.pool.Query(ctx, `
		SELECT
			o.username,
			COUNT(c.id) AS total_cases,
			COUNT(c.id) FILTER (WHERE c.current_stage = 'WATCH') AS watch_cases,
			COUNT(c.id) FILTER (WHERE c.current_stage = 'SUBSTANDARD') AS substandard_cases,
			COUNT(c.id) FILTER (WHERE c.current_stage = 'DOUBTFUL') AS doubtful_cases,
			COUNT(c.id) FILTER (WHERE c.current_stage = 'LOSS') AS loss_cases
		FROM collection_officers o
		LEFT JOIN collection_cases c
			ON c.assigned_to = o.username
			AND c.tenant_id = o.tenant_id
			AND c.status NOT IN ('CLOSED','WRITTEN_OFF')
		WHERE o.tenant_id=$1 AND o.is_active=true
		GROUP BY o.username
		ORDER BY o.username`, tenantID)
	if err != nil {
		return nil, fmt.Errorf("query workload: %w", err)
	}
	defer rows.Close()

	var results []model.OfficerWorkload
	for rows.Next() {
		var w model.OfficerWorkload
		if err := rows.Scan(&w.Username, &w.TotalCases, &w.WatchCases, &w.SubstandardCases, &w.DoubtfulCases, &w.LossCases); err != nil {
			return nil, fmt.Errorf("scan workload: %w", err)
		}
		results = append(results, w)
	}
	return results, rows.Err()
}

func scanOfficer(row pgx.Row) (*model.CollectionOfficer, error) {
	var o model.CollectionOfficer
	err := row.Scan(&o.ID, &o.TenantID, &o.Username, &o.MaxCases, &o.IsActive, &o.CreatedAt, &o.UpdatedAt)
	if err != nil {
		return nil, err
	}
	return &o, nil
}

func (r *OfficerRepository) scanOne(ctx context.Context, query string, args ...any) (*model.CollectionOfficer, error) {
	row := r.pool.QueryRow(ctx, query, args...)
	o, err := scanOfficer(row)
	if err != nil {
		if err == pgx.ErrNoRows {
			return nil, nil
		}
		return nil, fmt.Errorf("query officer: %w", err)
	}
	return o, nil
}

func (r *OfficerRepository) scanMany(ctx context.Context, query string, args ...any) ([]*model.CollectionOfficer, error) {
	rows, err := r.pool.Query(ctx, query, args...)
	if err != nil {
		return nil, fmt.Errorf("query officers: %w", err)
	}
	defer rows.Close()

	var results []*model.CollectionOfficer
	for rows.Next() {
		o, err := scanOfficer(rows)
		if err != nil {
			return nil, fmt.Errorf("scan officer: %w", err)
		}
		results = append(results, o)
	}
	return results, rows.Err()
}
