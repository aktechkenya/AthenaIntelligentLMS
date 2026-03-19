package repository

import (
	"context"
	"fmt"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"

	"github.com/athena-lms/go-services/internal/product/model"
)

// ─── Deposit Product ────────────────────────────────────────────────────────

func (r *Repository) CreateDepositProduct(ctx context.Context, p *model.DepositProduct) error {
	tx, err := r.pool.Begin(ctx)
	if err != nil {
		return fmt.Errorf("begin tx: %w", err)
	}
	defer tx.Rollback(ctx)

	err = tx.QueryRow(ctx, `
		INSERT INTO deposit_products (
			id, tenant_id, product_code, name, description, product_category, status,
			currency, interest_rate, interest_calc_method, interest_posting_freq,
			interest_compound_freq, accrual_frequency,
			min_opening_balance, min_operating_balance, min_balance_for_interest,
			min_term_days, max_term_days, early_withdrawal_penalty_rate, auto_renew,
			dormancy_days_threshold, dormancy_charge_amount,
			monthly_maintenance_fee, max_withdrawals_per_month,
			version, created_by, created_at, updated_at
		) VALUES (
			$1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14,$15,$16,$17,$18,$19,$20,
			$21,$22,$23,$24,$25,$26, NOW(), NOW()
		) RETURNING created_at, updated_at`,
		p.ID, p.TenantID, p.ProductCode, p.Name, p.Description, p.ProductCategory, p.Status,
		p.Currency, p.InterestRate.String(), p.InterestCalcMethod, p.InterestPostingFreq,
		p.InterestCompoundFreq, p.AccrualFrequency,
		p.MinOpeningBalance.String(), p.MinOperatingBalance.String(), p.MinBalanceForInterest.String(),
		p.MinTermDays, p.MaxTermDays, decimalPtr(p.EarlyWithdrawalPenaltyRate), p.AutoRenew,
		p.DormancyDaysThreshold, decimalPtr(p.DormancyChargeAmount),
		decimalPtr(p.MonthlyMaintenanceFee), p.MaxWithdrawalsPerMonth,
		p.Version, p.CreatedBy,
	).Scan(&p.CreatedAt, &p.UpdatedAt)
	if err != nil {
		return fmt.Errorf("insert deposit product: %w", err)
	}

	for i := range p.InterestTiers {
		tier := &p.InterestTiers[i]
		tier.ID = uuid.New()
		tier.ProductID = p.ID
		_, err = tx.Exec(ctx, `
			INSERT INTO deposit_interest_tiers (id, product_id, from_amount, to_amount, rate)
			VALUES ($1,$2,$3,$4,$5)`,
			tier.ID, tier.ProductID, tier.FromAmount.String(), tier.ToAmount.String(), tier.Rate.String(),
		)
		if err != nil {
			return fmt.Errorf("insert tier: %w", err)
		}
	}

	return tx.Commit(ctx)
}

func (r *Repository) GetDepositProductByIDAndTenant(ctx context.Context, id uuid.UUID, tenantID string) (*model.DepositProduct, error) {
	p := &model.DepositProduct{}
	err := r.pool.QueryRow(ctx, `
		SELECT id, tenant_id, product_code, name, description, product_category, status,
			currency, interest_rate, interest_calc_method, interest_posting_freq,
			interest_compound_freq, accrual_frequency,
			min_opening_balance, min_operating_balance, min_balance_for_interest,
			min_term_days, max_term_days, early_withdrawal_penalty_rate, auto_renew,
			dormancy_days_threshold, dormancy_charge_amount,
			monthly_maintenance_fee, max_withdrawals_per_month,
			version, created_by, created_at, updated_at
		FROM deposit_products WHERE id = $1 AND tenant_id = $2`,
		id, tenantID,
	).Scan(
		&p.ID, &p.TenantID, &p.ProductCode, &p.Name, &p.Description, &p.ProductCategory, &p.Status,
		&p.Currency, &p.InterestRate, &p.InterestCalcMethod, &p.InterestPostingFreq,
		&p.InterestCompoundFreq, &p.AccrualFrequency,
		&p.MinOpeningBalance, &p.MinOperatingBalance, &p.MinBalanceForInterest,
		&p.MinTermDays, &p.MaxTermDays, &p.EarlyWithdrawalPenaltyRate, &p.AutoRenew,
		&p.DormancyDaysThreshold, &p.DormancyChargeAmount,
		&p.MonthlyMaintenanceFee, &p.MaxWithdrawalsPerMonth,
		&p.Version, &p.CreatedBy, &p.CreatedAt, &p.UpdatedAt,
	)
	if err == pgx.ErrNoRows {
		return nil, nil
	}
	if err != nil {
		return nil, fmt.Errorf("get deposit product: %w", err)
	}

	tiers, err := r.getDepositInterestTiers(ctx, p.ID)
	if err != nil {
		return nil, err
	}
	p.InterestTiers = tiers
	return p, nil
}

func (r *Repository) ExistsDepositProductByCodeAndTenant(ctx context.Context, code, tenantID string) (bool, error) {
	var exists bool
	err := r.pool.QueryRow(ctx,
		`SELECT EXISTS(SELECT 1 FROM deposit_products WHERE product_code = $1 AND tenant_id = $2)`,
		code, tenantID,
	).Scan(&exists)
	return exists, err
}

func (r *Repository) ListDepositProductsByTenant(ctx context.Context, tenantID string, page, size int) ([]model.DepositProduct, int64, error) {
	var total int64
	err := r.pool.QueryRow(ctx,
		`SELECT COUNT(*) FROM deposit_products WHERE tenant_id = $1`, tenantID,
	).Scan(&total)
	if err != nil {
		return nil, 0, fmt.Errorf("count deposit products: %w", err)
	}

	rows, err := r.pool.Query(ctx, `
		SELECT id, tenant_id, product_code, name, description, product_category, status,
			currency, interest_rate, interest_calc_method, interest_posting_freq,
			interest_compound_freq, accrual_frequency,
			min_opening_balance, min_operating_balance, min_balance_for_interest,
			min_term_days, max_term_days, early_withdrawal_penalty_rate, auto_renew,
			dormancy_days_threshold, dormancy_charge_amount,
			monthly_maintenance_fee, max_withdrawals_per_month,
			version, created_by, created_at, updated_at
		FROM deposit_products WHERE tenant_id = $1
		ORDER BY created_at DESC
		LIMIT $2 OFFSET $3`,
		tenantID, size, page*size,
	)
	if err != nil {
		return nil, 0, fmt.Errorf("list deposit products: %w", err)
	}
	defer rows.Close()

	var products []model.DepositProduct
	for rows.Next() {
		var p model.DepositProduct
		if err := rows.Scan(
			&p.ID, &p.TenantID, &p.ProductCode, &p.Name, &p.Description, &p.ProductCategory, &p.Status,
			&p.Currency, &p.InterestRate, &p.InterestCalcMethod, &p.InterestPostingFreq,
			&p.InterestCompoundFreq, &p.AccrualFrequency,
			&p.MinOpeningBalance, &p.MinOperatingBalance, &p.MinBalanceForInterest,
			&p.MinTermDays, &p.MaxTermDays, &p.EarlyWithdrawalPenaltyRate, &p.AutoRenew,
			&p.DormancyDaysThreshold, &p.DormancyChargeAmount,
			&p.MonthlyMaintenanceFee, &p.MaxWithdrawalsPerMonth,
			&p.Version, &p.CreatedBy, &p.CreatedAt, &p.UpdatedAt,
		); err != nil {
			return nil, 0, fmt.Errorf("scan deposit product: %w", err)
		}
		tiers, err := r.getDepositInterestTiers(ctx, p.ID)
		if err != nil {
			return nil, 0, err
		}
		p.InterestTiers = tiers
		products = append(products, p)
	}
	if products == nil {
		products = []model.DepositProduct{}
	}
	return products, total, rows.Err()
}

func (r *Repository) UpdateDepositProduct(ctx context.Context, p *model.DepositProduct) error {
	tx, err := r.pool.Begin(ctx)
	if err != nil {
		return fmt.Errorf("begin tx: %w", err)
	}
	defer tx.Rollback(ctx)

	_, err = tx.Exec(ctx, `
		UPDATE deposit_products SET
			name=$1, description=$2, product_category=$3, status=$4,
			currency=$5, interest_rate=$6, interest_calc_method=$7,
			interest_posting_freq=$8, interest_compound_freq=$9, accrual_frequency=$10,
			min_opening_balance=$11, min_operating_balance=$12, min_balance_for_interest=$13,
			min_term_days=$14, max_term_days=$15, early_withdrawal_penalty_rate=$16,
			auto_renew=$17, dormancy_days_threshold=$18, dormancy_charge_amount=$19,
			monthly_maintenance_fee=$20, max_withdrawals_per_month=$21,
			version=$22, updated_at=NOW()
		WHERE id=$23 AND tenant_id=$24`,
		p.Name, p.Description, p.ProductCategory, p.Status,
		p.Currency, p.InterestRate.String(), p.InterestCalcMethod,
		p.InterestPostingFreq, p.InterestCompoundFreq, p.AccrualFrequency,
		p.MinOpeningBalance.String(), p.MinOperatingBalance.String(), p.MinBalanceForInterest.String(),
		p.MinTermDays, p.MaxTermDays, decimalPtr(p.EarlyWithdrawalPenaltyRate),
		p.AutoRenew, p.DormancyDaysThreshold, decimalPtr(p.DormancyChargeAmount),
		decimalPtr(p.MonthlyMaintenanceFee), p.MaxWithdrawalsPerMonth,
		p.Version, p.ID, p.TenantID,
	)
	if err != nil {
		return fmt.Errorf("update deposit product: %w", err)
	}

	// Replace tiers
	_, err = tx.Exec(ctx, `DELETE FROM deposit_interest_tiers WHERE product_id = $1`, p.ID)
	if err != nil {
		return fmt.Errorf("delete old tiers: %w", err)
	}

	for i := range p.InterestTiers {
		tier := &p.InterestTiers[i]
		tier.ID = uuid.New()
		tier.ProductID = p.ID
		_, err = tx.Exec(ctx, `
			INSERT INTO deposit_interest_tiers (id, product_id, from_amount, to_amount, rate)
			VALUES ($1,$2,$3,$4,$5)`,
			tier.ID, tier.ProductID, tier.FromAmount.String(), tier.ToAmount.String(), tier.Rate.String(),
		)
		if err != nil {
			return fmt.Errorf("insert tier: %w", err)
		}
	}

	return tx.Commit(ctx)
}

func (r *Repository) getDepositInterestTiers(ctx context.Context, productID uuid.UUID) ([]model.DepositInterestTier, error) {
	rows, err := r.pool.Query(ctx, `
		SELECT id, product_id, from_amount, to_amount, rate
		FROM deposit_interest_tiers WHERE product_id = $1 ORDER BY from_amount`,
		productID,
	)
	if err != nil {
		return nil, fmt.Errorf("get deposit tiers: %w", err)
	}
	defer rows.Close()

	var tiers []model.DepositInterestTier
	for rows.Next() {
		var t model.DepositInterestTier
		if err := rows.Scan(&t.ID, &t.ProductID, &t.FromAmount, &t.ToAmount, &t.Rate); err != nil {
			return nil, fmt.Errorf("scan deposit tier: %w", err)
		}
		tiers = append(tiers, t)
	}
	if tiers == nil {
		tiers = []model.DepositInterestTier{}
	}
	return tiers, rows.Err()
}
