package repository

import (
	"context"
	"encoding/json"
	"fmt"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/shopspring/decimal"

	"github.com/athena-lms/go-services/internal/product/model"
)

// Repository provides all data-access methods for the product service.
type Repository struct {
	pool *pgxpool.Pool
}

// New creates a new Repository.
func New(pool *pgxpool.Pool) *Repository {
	return &Repository{pool: pool}
}

// ─── Product ────────────────────────────────────────────────────────────────

func (r *Repository) CreateProduct(ctx context.Context, p *model.Product) error {
	tx, err := r.pool.Begin(ctx)
	if err != nil {
		return fmt.Errorf("begin tx: %w", err)
	}
	defer tx.Rollback(ctx)

	err = tx.QueryRow(ctx, `
		INSERT INTO products (
			id, tenant_id, product_code, name, product_type, status, description,
			currency, min_amount, max_amount, min_tenor_days, max_tenor_days,
			schedule_type, repayment_frequency, nominal_rate, penalty_rate,
			penalty_grace_days, grace_period_days, processing_fee_rate,
			processing_fee_min, processing_fee_max, requires_collateral,
			min_credit_score, max_dtir, version, template_id,
			requires_two_person_auth, auth_threshold_amount, pending_authorization,
			created_by, created_at, updated_at
		) VALUES (
			$1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14,$15,$16,$17,$18,$19,$20,
			$21,$22,$23,$24,$25,$26,$27,$28,$29,$30, NOW(), NOW()
		) RETURNING created_at, updated_at`,
		p.ID, p.TenantID, p.ProductCode, p.Name, p.ProductType, p.Status,
		p.Description, p.Currency, decimalPtr(p.MinAmount), decimalPtr(p.MaxAmount),
		p.MinTenorDays, p.MaxTenorDays, p.ScheduleType, p.RepaymentFrequency,
		p.NominalRate, p.PenaltyRate, p.PenaltyGraceDays, p.GracePeriodDays,
		p.ProcessingFeeRate, p.ProcessingFeeMin, decimalPtr(p.ProcessingFeeMax),
		p.RequiresCollateral, p.MinCreditScore, p.MaxDtir, p.Version,
		p.TemplateID, p.RequiresTwoPersonAuth, decimalPtr(p.AuthThresholdAmount),
		p.PendingAuthorization, p.CreatedBy,
	).Scan(&p.CreatedAt, &p.UpdatedAt)
	if err != nil {
		return fmt.Errorf("insert product: %w", err)
	}

	for i := range p.Fees {
		fee := &p.Fees[i]
		fee.ID = uuid.New()
		fee.TenantID = p.TenantID
		fee.ProductID = p.ID
		err = tx.QueryRow(ctx, `
			INSERT INTO product_fees (id, tenant_id, product_id, fee_name, fee_type,
				calculation_type, amount, rate, is_mandatory, created_at)
			VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9, NOW())
			RETURNING created_at`,
			fee.ID, fee.TenantID, fee.ProductID, fee.FeeName, fee.FeeType,
			fee.CalculationType, decimalPtr(fee.Amount), decimalPtr(fee.Rate), fee.IsMandatory,
		).Scan(&fee.CreatedAt)
		if err != nil {
			return fmt.Errorf("insert fee: %w", err)
		}
	}

	return tx.Commit(ctx)
}

func (r *Repository) GetProductByIDAndTenant(ctx context.Context, id uuid.UUID, tenantID string) (*model.Product, error) {
	p := &model.Product{}
	err := r.pool.QueryRow(ctx, `
		SELECT id, tenant_id, product_code, name, product_type, status, description,
			currency, min_amount, max_amount, min_tenor_days, max_tenor_days,
			schedule_type, repayment_frequency, nominal_rate, penalty_rate,
			penalty_grace_days, grace_period_days, processing_fee_rate,
			processing_fee_min, processing_fee_max, requires_collateral,
			min_credit_score, max_dtir, version, template_id,
			requires_two_person_auth, auth_threshold_amount, pending_authorization,
			created_by, created_at, updated_at
		FROM products WHERE id = $1 AND tenant_id = $2`,
		id, tenantID,
	).Scan(
		&p.ID, &p.TenantID, &p.ProductCode, &p.Name, &p.ProductType, &p.Status,
		&p.Description, &p.Currency, &p.MinAmount, &p.MaxAmount,
		&p.MinTenorDays, &p.MaxTenorDays, &p.ScheduleType, &p.RepaymentFrequency,
		&p.NominalRate, &p.PenaltyRate, &p.PenaltyGraceDays, &p.GracePeriodDays,
		&p.ProcessingFeeRate, &p.ProcessingFeeMin, &p.ProcessingFeeMax,
		&p.RequiresCollateral, &p.MinCreditScore, &p.MaxDtir, &p.Version,
		&p.TemplateID, &p.RequiresTwoPersonAuth, &p.AuthThresholdAmount,
		&p.PendingAuthorization, &p.CreatedBy, &p.CreatedAt, &p.UpdatedAt,
	)
	if err == pgx.ErrNoRows {
		return nil, nil
	}
	if err != nil {
		return nil, fmt.Errorf("get product: %w", err)
	}

	fees, err := r.getProductFees(ctx, p.ID)
	if err != nil {
		return nil, err
	}
	p.Fees = fees
	return p, nil
}

func (r *Repository) ExistsProductByCodeAndTenant(ctx context.Context, code, tenantID string) (bool, error) {
	var exists bool
	err := r.pool.QueryRow(ctx,
		`SELECT EXISTS(SELECT 1 FROM products WHERE product_code = $1 AND tenant_id = $2)`,
		code, tenantID,
	).Scan(&exists)
	return exists, err
}

func (r *Repository) ListProductsByTenant(ctx context.Context, tenantID string, page, size int) ([]model.Product, int64, error) {
	var total int64
	err := r.pool.QueryRow(ctx,
		`SELECT COUNT(*) FROM products WHERE tenant_id = $1`, tenantID,
	).Scan(&total)
	if err != nil {
		return nil, 0, fmt.Errorf("count products: %w", err)
	}

	rows, err := r.pool.Query(ctx, `
		SELECT id, tenant_id, product_code, name, product_type, status, description,
			currency, min_amount, max_amount, min_tenor_days, max_tenor_days,
			schedule_type, repayment_frequency, nominal_rate, penalty_rate,
			penalty_grace_days, grace_period_days, processing_fee_rate,
			processing_fee_min, processing_fee_max, requires_collateral,
			min_credit_score, max_dtir, version, template_id,
			requires_two_person_auth, auth_threshold_amount, pending_authorization,
			created_by, created_at, updated_at
		FROM products WHERE tenant_id = $1
		ORDER BY created_at DESC
		LIMIT $2 OFFSET $3`,
		tenantID, size, page*size,
	)
	if err != nil {
		return nil, 0, fmt.Errorf("list products: %w", err)
	}
	defer rows.Close()

	products, err := scanProducts(rows)
	if err != nil {
		return nil, 0, err
	}

	// Load fees for each product
	for i := range products {
		fees, err := r.getProductFees(ctx, products[i].ID)
		if err != nil {
			return nil, 0, err
		}
		products[i].Fees = fees
	}

	return products, total, nil
}

func (r *Repository) UpdateProduct(ctx context.Context, p *model.Product) error {
	_, err := r.pool.Exec(ctx, `
		UPDATE products SET
			name=$1, description=$2, min_amount=$3, max_amount=$4,
			min_tenor_days=$5, max_tenor_days=$6, nominal_rate=$7, penalty_rate=$8,
			schedule_type=$9, repayment_frequency=$10, status=$11, version=$12,
			pending_authorization=$13, updated_at=NOW()
		WHERE id=$14 AND tenant_id=$15`,
		p.Name, p.Description, decimalPtr(p.MinAmount), decimalPtr(p.MaxAmount),
		p.MinTenorDays, p.MaxTenorDays, p.NominalRate, p.PenaltyRate,
		p.ScheduleType, p.RepaymentFrequency, p.Status, p.Version,
		p.PendingAuthorization, p.ID, p.TenantID,
	)
	return err
}

func (r *Repository) getProductFees(ctx context.Context, productID uuid.UUID) ([]model.ProductFee, error) {
	rows, err := r.pool.Query(ctx, `
		SELECT id, tenant_id, product_id, fee_name, fee_type, calculation_type,
			amount, rate, is_mandatory, created_at
		FROM product_fees WHERE product_id = $1`,
		productID,
	)
	if err != nil {
		return nil, fmt.Errorf("get fees: %w", err)
	}
	defer rows.Close()

	var fees []model.ProductFee
	for rows.Next() {
		var f model.ProductFee
		if err := rows.Scan(
			&f.ID, &f.TenantID, &f.ProductID, &f.FeeName, &f.FeeType,
			&f.CalculationType, &f.Amount, &f.Rate, &f.IsMandatory, &f.CreatedAt,
		); err != nil {
			return nil, fmt.Errorf("scan fee: %w", err)
		}
		fees = append(fees, f)
	}
	if fees == nil {
		fees = []model.ProductFee{}
	}
	return fees, rows.Err()
}

// ─── Product Versions ───────────────────────────────────────────────────────

func (r *Repository) SaveVersion(ctx context.Context, v *model.ProductVersion) error {
	return r.pool.QueryRow(ctx, `
		INSERT INTO product_versions (id, product_id, version_number, snapshot, changed_by, change_reason, created_at)
		VALUES ($1,$2,$3,$4,$5,$6, NOW())
		RETURNING created_at`,
		v.ID, v.ProductID, v.VersionNumber, v.Snapshot, v.ChangedBy, v.ChangeReason,
	).Scan(&v.CreatedAt)
}

func (r *Repository) ListVersionsByProduct(ctx context.Context, productID uuid.UUID) ([]model.ProductVersion, error) {
	rows, err := r.pool.Query(ctx, `
		SELECT id, product_id, version_number, snapshot, changed_by, change_reason, created_at
		FROM product_versions WHERE product_id = $1
		ORDER BY version_number DESC`,
		productID,
	)
	if err != nil {
		return nil, fmt.Errorf("list versions: %w", err)
	}
	defer rows.Close()

	var versions []model.ProductVersion
	for rows.Next() {
		var v model.ProductVersion
		if err := rows.Scan(
			&v.ID, &v.ProductID, &v.VersionNumber, &v.Snapshot, &v.ChangedBy, &v.ChangeReason, &v.CreatedAt,
		); err != nil {
			return nil, fmt.Errorf("scan version: %w", err)
		}
		versions = append(versions, v)
	}
	if versions == nil {
		versions = []model.ProductVersion{}
	}
	return versions, rows.Err()
}

// ─── Product Templates ─────────────────────────────────────────────────────

func (r *Repository) GetTemplateByCode(ctx context.Context, code string) (*model.ProductTemplate, error) {
	t := &model.ProductTemplate{}
	err := r.pool.QueryRow(ctx, `
		SELECT id, template_code, name, product_type, configuration, is_active, created_at
		FROM product_templates WHERE template_code = $1`,
		code,
	).Scan(&t.ID, &t.TemplateCode, &t.Name, &t.ProductType, &t.Configuration, &t.IsActive, &t.CreatedAt)
	if err == pgx.ErrNoRows {
		return nil, nil
	}
	if err != nil {
		return nil, fmt.Errorf("get template: %w", err)
	}
	return t, nil
}

func (r *Repository) ListActiveTemplates(ctx context.Context) ([]model.ProductTemplate, error) {
	rows, err := r.pool.Query(ctx, `
		SELECT id, template_code, name, product_type, configuration, is_active, created_at
		FROM product_templates WHERE is_active = true`)
	if err != nil {
		return nil, fmt.Errorf("list templates: %w", err)
	}
	defer rows.Close()

	var templates []model.ProductTemplate
	for rows.Next() {
		var t model.ProductTemplate
		if err := rows.Scan(&t.ID, &t.TemplateCode, &t.Name, &t.ProductType, &t.Configuration, &t.IsActive, &t.CreatedAt); err != nil {
			return nil, fmt.Errorf("scan template: %w", err)
		}
		templates = append(templates, t)
	}
	if templates == nil {
		templates = []model.ProductTemplate{}
	}
	return templates, rows.Err()
}

// ─── Transaction Charges ────────────────────────────────────────────────────

func (r *Repository) CreateCharge(ctx context.Context, c *model.TransactionCharge) error {
	tx, err := r.pool.Begin(ctx)
	if err != nil {
		return fmt.Errorf("begin tx: %w", err)
	}
	defer tx.Rollback(ctx)

	err = tx.QueryRow(ctx, `
		INSERT INTO transaction_charges (
			id, tenant_id, charge_code, charge_name, transaction_type, calculation_type,
			flat_amount, percentage_rate, min_amount, max_amount, currency,
			is_active, effective_from, effective_to, created_at, updated_at
		) VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14, NOW(), NOW())
		RETURNING created_at, updated_at`,
		c.ID, c.TenantID, c.ChargeCode, c.ChargeName, c.TransactionType, c.CalculationType,
		decimalPtr(c.FlatAmount), decimalPtr(c.PercentageRate), decimalPtr(c.MinAmount),
		decimalPtr(c.MaxAmount), c.Currency, c.IsActive, c.EffectiveFrom, c.EffectiveTo,
	).Scan(&c.CreatedAt, &c.UpdatedAt)
	if err != nil {
		return fmt.Errorf("insert charge: %w", err)
	}

	for i := range c.Tiers {
		tier := &c.Tiers[i]
		tier.ID = uuid.New()
		tier.ChargeID = c.ID
		_, err = tx.Exec(ctx, `
			INSERT INTO charge_tiers (id, charge_id, from_amount, to_amount, flat_amount, percentage_rate)
			VALUES ($1,$2,$3,$4,$5,$6)`,
			tier.ID, tier.ChargeID, tier.FromAmount, tier.ToAmount,
			decimalPtr(tier.FlatAmount), decimalPtr(tier.PercentageRate),
		)
		if err != nil {
			return fmt.Errorf("insert tier: %w", err)
		}
	}

	return tx.Commit(ctx)
}

func (r *Repository) GetChargeByIDAndTenant(ctx context.Context, id uuid.UUID, tenantID string) (*model.TransactionCharge, error) {
	c := &model.TransactionCharge{}
	err := r.pool.QueryRow(ctx, `
		SELECT id, tenant_id, charge_code, charge_name, transaction_type, calculation_type,
			flat_amount, percentage_rate, min_amount, max_amount, currency,
			is_active, effective_from, effective_to, created_at, updated_at
		FROM transaction_charges WHERE id = $1 AND tenant_id = $2`,
		id, tenantID,
	).Scan(
		&c.ID, &c.TenantID, &c.ChargeCode, &c.ChargeName, &c.TransactionType, &c.CalculationType,
		&c.FlatAmount, &c.PercentageRate, &c.MinAmount, &c.MaxAmount, &c.Currency,
		&c.IsActive, &c.EffectiveFrom, &c.EffectiveTo, &c.CreatedAt, &c.UpdatedAt,
	)
	if err == pgx.ErrNoRows {
		return nil, nil
	}
	if err != nil {
		return nil, fmt.Errorf("get charge: %w", err)
	}

	tiers, err := r.getChargeTiers(ctx, c.ID)
	if err != nil {
		return nil, err
	}
	c.Tiers = tiers
	return c, nil
}

func (r *Repository) ExistsChargeByCodeAndTenant(ctx context.Context, code, tenantID string) (bool, error) {
	var exists bool
	err := r.pool.QueryRow(ctx,
		`SELECT EXISTS(SELECT 1 FROM transaction_charges WHERE charge_code = $1 AND tenant_id = $2)`,
		code, tenantID,
	).Scan(&exists)
	return exists, err
}

func (r *Repository) ListChargesByTenant(ctx context.Context, tenantID string, page, size int) ([]model.TransactionCharge, int64, error) {
	var total int64
	err := r.pool.QueryRow(ctx,
		`SELECT COUNT(*) FROM transaction_charges WHERE tenant_id = $1`, tenantID,
	).Scan(&total)
	if err != nil {
		return nil, 0, fmt.Errorf("count charges: %w", err)
	}

	rows, err := r.pool.Query(ctx, `
		SELECT id, tenant_id, charge_code, charge_name, transaction_type, calculation_type,
			flat_amount, percentage_rate, min_amount, max_amount, currency,
			is_active, effective_from, effective_to, created_at, updated_at
		FROM transaction_charges WHERE tenant_id = $1
		ORDER BY created_at DESC
		LIMIT $2 OFFSET $3`,
		tenantID, size, page*size,
	)
	if err != nil {
		return nil, 0, fmt.Errorf("list charges: %w", err)
	}
	defer rows.Close()

	var charges []model.TransactionCharge
	for rows.Next() {
		var c model.TransactionCharge
		if err := rows.Scan(
			&c.ID, &c.TenantID, &c.ChargeCode, &c.ChargeName, &c.TransactionType, &c.CalculationType,
			&c.FlatAmount, &c.PercentageRate, &c.MinAmount, &c.MaxAmount, &c.Currency,
			&c.IsActive, &c.EffectiveFrom, &c.EffectiveTo, &c.CreatedAt, &c.UpdatedAt,
		); err != nil {
			return nil, 0, fmt.Errorf("scan charge: %w", err)
		}
		tiers, err := r.getChargeTiers(ctx, c.ID)
		if err != nil {
			return nil, 0, err
		}
		c.Tiers = tiers
		charges = append(charges, c)
	}
	if charges == nil {
		charges = []model.TransactionCharge{}
	}
	return charges, total, rows.Err()
}

func (r *Repository) UpdateCharge(ctx context.Context, c *model.TransactionCharge) error {
	tx, err := r.pool.Begin(ctx)
	if err != nil {
		return fmt.Errorf("begin tx: %w", err)
	}
	defer tx.Rollback(ctx)

	_, err = tx.Exec(ctx, `
		UPDATE transaction_charges SET
			charge_name=$1, transaction_type=$2, calculation_type=$3,
			flat_amount=$4, percentage_rate=$5, min_amount=$6, max_amount=$7,
			currency=$8, updated_at=NOW()
		WHERE id=$9 AND tenant_id=$10`,
		c.ChargeName, c.TransactionType, c.CalculationType,
		decimalPtr(c.FlatAmount), decimalPtr(c.PercentageRate), decimalPtr(c.MinAmount),
		decimalPtr(c.MaxAmount), c.Currency, c.ID, c.TenantID,
	)
	if err != nil {
		return fmt.Errorf("update charge: %w", err)
	}

	// Replace tiers
	_, err = tx.Exec(ctx, `DELETE FROM charge_tiers WHERE charge_id = $1`, c.ID)
	if err != nil {
		return fmt.Errorf("delete old tiers: %w", err)
	}

	for i := range c.Tiers {
		tier := &c.Tiers[i]
		tier.ID = uuid.New()
		tier.ChargeID = c.ID
		_, err = tx.Exec(ctx, `
			INSERT INTO charge_tiers (id, charge_id, from_amount, to_amount, flat_amount, percentage_rate)
			VALUES ($1,$2,$3,$4,$5,$6)`,
			tier.ID, tier.ChargeID, tier.FromAmount, tier.ToAmount,
			decimalPtr(tier.FlatAmount), decimalPtr(tier.PercentageRate),
		)
		if err != nil {
			return fmt.Errorf("insert tier: %w", err)
		}
	}

	return tx.Commit(ctx)
}

func (r *Repository) DeleteCharge(ctx context.Context, id uuid.UUID, tenantID string) error {
	_, err := r.pool.Exec(ctx,
		`DELETE FROM transaction_charges WHERE id = $1 AND tenant_id = $2`,
		id, tenantID,
	)
	return err
}

func (r *Repository) FindActiveChargesByTransactionType(ctx context.Context, tenantID string, txnType model.ChargeTransactionType) ([]model.TransactionCharge, error) {
	rows, err := r.pool.Query(ctx, `
		SELECT id, tenant_id, charge_code, charge_name, transaction_type, calculation_type,
			flat_amount, percentage_rate, min_amount, max_amount, currency,
			is_active, effective_from, effective_to, created_at, updated_at
		FROM transaction_charges
		WHERE tenant_id = $1 AND transaction_type = $2 AND is_active = true`,
		tenantID, txnType,
	)
	if err != nil {
		return nil, fmt.Errorf("find charges: %w", err)
	}
	defer rows.Close()

	var charges []model.TransactionCharge
	for rows.Next() {
		var c model.TransactionCharge
		if err := rows.Scan(
			&c.ID, &c.TenantID, &c.ChargeCode, &c.ChargeName, &c.TransactionType, &c.CalculationType,
			&c.FlatAmount, &c.PercentageRate, &c.MinAmount, &c.MaxAmount, &c.Currency,
			&c.IsActive, &c.EffectiveFrom, &c.EffectiveTo, &c.CreatedAt, &c.UpdatedAt,
		); err != nil {
			return nil, fmt.Errorf("scan charge: %w", err)
		}
		tiers, err := r.getChargeTiers(ctx, c.ID)
		if err != nil {
			return nil, err
		}
		c.Tiers = tiers
		charges = append(charges, c)
	}
	return charges, rows.Err()
}

func (r *Repository) getChargeTiers(ctx context.Context, chargeID uuid.UUID) ([]model.ChargeTier, error) {
	rows, err := r.pool.Query(ctx, `
		SELECT id, charge_id, from_amount, to_amount, flat_amount, percentage_rate
		FROM charge_tiers WHERE charge_id = $1`,
		chargeID,
	)
	if err != nil {
		return nil, fmt.Errorf("get tiers: %w", err)
	}
	defer rows.Close()

	var tiers []model.ChargeTier
	for rows.Next() {
		var t model.ChargeTier
		if err := rows.Scan(&t.ID, &t.ChargeID, &t.FromAmount, &t.ToAmount, &t.FlatAmount, &t.PercentageRate); err != nil {
			return nil, fmt.Errorf("scan tier: %w", err)
		}
		tiers = append(tiers, t)
	}
	if tiers == nil {
		tiers = []model.ChargeTier{}
	}
	return tiers, rows.Err()
}

// ─── Helpers ────────────────────────────────────────────────────────────────

func scanProducts(rows pgx.Rows) ([]model.Product, error) {
	var products []model.Product
	for rows.Next() {
		var p model.Product
		if err := rows.Scan(
			&p.ID, &p.TenantID, &p.ProductCode, &p.Name, &p.ProductType, &p.Status,
			&p.Description, &p.Currency, &p.MinAmount, &p.MaxAmount,
			&p.MinTenorDays, &p.MaxTenorDays, &p.ScheduleType, &p.RepaymentFrequency,
			&p.NominalRate, &p.PenaltyRate, &p.PenaltyGraceDays, &p.GracePeriodDays,
			&p.ProcessingFeeRate, &p.ProcessingFeeMin, &p.ProcessingFeeMax,
			&p.RequiresCollateral, &p.MinCreditScore, &p.MaxDtir, &p.Version,
			&p.TemplateID, &p.RequiresTwoPersonAuth, &p.AuthThresholdAmount,
			&p.PendingAuthorization, &p.CreatedBy, &p.CreatedAt, &p.UpdatedAt,
		); err != nil {
			return nil, fmt.Errorf("scan product: %w", err)
		}
		products = append(products, p)
	}
	if products == nil {
		products = []model.Product{}
	}
	return products, rows.Err()
}

// decimalPtr extracts the underlying string for pgx if the pointer is non-nil.
// pgx natively supports *decimal.Decimal scanning but needs help with inserts.
func decimalPtr(d *decimal.Decimal) any {
	if d == nil {
		return nil
	}
	return d.String()
}

// snapshotJSON marshals a ProductResponse to json.RawMessage for version snapshots.
func SnapshotJSON(resp model.ProductResponse) (json.RawMessage, error) {
	b, err := json.Marshal(resp)
	if err != nil {
		return nil, err
	}
	return json.RawMessage(b), nil
}
