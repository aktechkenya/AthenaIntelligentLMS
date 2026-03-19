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

// StrategyRepository provides data access for collection_strategies.
type StrategyRepository struct {
	pool *pgxpool.Pool
}

// NewStrategyRepository creates a new StrategyRepository.
func NewStrategyRepository(pool *pgxpool.Pool) *StrategyRepository {
	return &StrategyRepository{pool: pool}
}

// Save inserts or updates a collection strategy.
func (r *StrategyRepository) Save(ctx context.Context, s *model.CollectionStrategy) (*model.CollectionStrategy, error) {
	now := time.Now().UTC()
	if s.ID == uuid.Nil {
		s.ID = uuid.New()
		s.CreatedAt = now
		s.UpdatedAt = now
		_, err := r.pool.Exec(ctx, `
			INSERT INTO collection_strategies
				(id, tenant_id, name, product_type, dpd_from, dpd_to,
				 action_type, priority, is_active, created_at, updated_at)
			VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11)`,
			s.ID, s.TenantID, s.Name, s.ProductType, s.DpdFrom, s.DpdTo,
			string(s.ActionType), s.Priority, s.IsActive, s.CreatedAt, s.UpdatedAt,
		)
		if err != nil {
			return nil, fmt.Errorf("insert strategy: %w", err)
		}
		return s, nil
	}

	s.UpdatedAt = now
	_, err := r.pool.Exec(ctx, `
		UPDATE collection_strategies SET
			name=$2, product_type=$3, dpd_from=$4, dpd_to=$5,
			action_type=$6, priority=$7, is_active=$8, updated_at=$9
		WHERE id=$1`,
		s.ID, s.Name, s.ProductType, s.DpdFrom, s.DpdTo,
		string(s.ActionType), s.Priority, s.IsActive, s.UpdatedAt,
	)
	if err != nil {
		return nil, fmt.Errorf("update strategy: %w", err)
	}
	return s, nil
}

// FindByTenantID returns all strategies for a tenant, ordered by priority.
func (r *StrategyRepository) FindByTenantID(ctx context.Context, tenantID string) ([]*model.CollectionStrategy, error) {
	return r.scanMany(ctx, `
		SELECT `+strategyColumns+`
		FROM collection_strategies
		WHERE tenant_id=$1
		ORDER BY priority ASC, created_at ASC`, tenantID)
}

// FindByTenantIDAndID returns a single strategy by tenant and ID.
func (r *StrategyRepository) FindByTenantIDAndID(ctx context.Context, tenantID string, id uuid.UUID) (*model.CollectionStrategy, error) {
	return r.scanOne(ctx, `
		SELECT `+strategyColumns+`
		FROM collection_strategies
		WHERE tenant_id=$1 AND id=$2`, tenantID, id)
}

// FindActiveByTenantIDAndDPD returns active strategies matching the given DPD range and optional product type.
func (r *StrategyRepository) FindActiveByTenantIDAndDPD(ctx context.Context, tenantID string, dpd int, productType *string) ([]*model.CollectionStrategy, error) {
	if productType != nil {
		return r.scanMany(ctx, `
			SELECT `+strategyColumns+`
			FROM collection_strategies
			WHERE tenant_id=$1 AND is_active=true
			  AND dpd_from <= $2 AND dpd_to >= $2
			  AND (product_type IS NULL OR product_type = $3)
			ORDER BY priority ASC`, tenantID, dpd, *productType)
	}
	return r.scanMany(ctx, `
		SELECT `+strategyColumns+`
		FROM collection_strategies
		WHERE tenant_id=$1 AND is_active=true
		  AND dpd_from <= $2 AND dpd_to >= $2
		  AND product_type IS NULL
		ORDER BY priority ASC`, tenantID, dpd)
}

// Delete removes a strategy by tenant and ID.
func (r *StrategyRepository) Delete(ctx context.Context, tenantID string, id uuid.UUID) error {
	_, err := r.pool.Exec(ctx, `
		DELETE FROM collection_strategies WHERE tenant_id=$1 AND id=$2`, tenantID, id)
	if err != nil {
		return fmt.Errorf("delete strategy: %w", err)
	}
	return nil
}

const strategyColumns = `id, tenant_id, name, product_type, dpd_from, dpd_to,
	action_type, priority, is_active, created_at, updated_at`

func scanStrategy(row pgx.Row) (*model.CollectionStrategy, error) {
	var s model.CollectionStrategy
	var actionType string
	err := row.Scan(
		&s.ID, &s.TenantID, &s.Name, &s.ProductType, &s.DpdFrom, &s.DpdTo,
		&actionType, &s.Priority, &s.IsActive, &s.CreatedAt, &s.UpdatedAt,
	)
	if err != nil {
		return nil, err
	}
	s.ActionType = model.ActionType(actionType)
	return &s, nil
}

func (r *StrategyRepository) scanOne(ctx context.Context, query string, args ...any) (*model.CollectionStrategy, error) {
	row := r.pool.QueryRow(ctx, query, args...)
	s, err := scanStrategy(row)
	if err != nil {
		if err == pgx.ErrNoRows {
			return nil, nil
		}
		return nil, fmt.Errorf("query strategy: %w", err)
	}
	return s, nil
}

func (r *StrategyRepository) scanMany(ctx context.Context, query string, args ...any) ([]*model.CollectionStrategy, error) {
	rows, err := r.pool.Query(ctx, query, args...)
	if err != nil {
		return nil, fmt.Errorf("query strategies: %w", err)
	}
	defer rows.Close()

	var results []*model.CollectionStrategy
	for rows.Next() {
		s, err := scanStrategy(rows)
		if err != nil {
			return nil, fmt.Errorf("scan strategy: %w", err)
		}
		results = append(results, s)
	}
	return results, rows.Err()
}
