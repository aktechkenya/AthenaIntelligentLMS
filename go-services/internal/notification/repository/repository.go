package repository

import (
	"context"
	"fmt"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"

	"github.com/athena-lms/go-services/internal/notification/model"
)

// Repository handles all notification persistence operations.
type Repository struct {
	pool *pgxpool.Pool
}

// New creates a new Repository.
func New(pool *pgxpool.Pool) *Repository {
	return &Repository{pool: pool}
}

// ---------- NotificationConfig ----------

// FindConfigByType returns the notification config for the given type (EMAIL, SMS).
func (r *Repository) FindConfigByType(ctx context.Context, configType string) (*model.NotificationConfig, error) {
	row := r.pool.QueryRow(ctx, `
		SELECT id, type, provider, host, port, username, password,
		       from_address, api_key, api_secret, sender_id, enabled
		FROM notification_configs
		WHERE type = $1`, configType)

	var c model.NotificationConfig
	err := row.Scan(
		&c.ID, &c.Type, &c.Provider, &c.Host, &c.Port,
		&c.Username, &c.Password, &c.FromAddress,
		&c.APIKey, &c.APISecret, &c.SenderID, &c.Enabled,
	)
	if err == pgx.ErrNoRows {
		return nil, nil
	}
	if err != nil {
		return nil, fmt.Errorf("find config by type: %w", err)
	}
	return &c, nil
}

// UpsertConfig inserts or updates a notification config by type.
func (r *Repository) UpsertConfig(ctx context.Context, c *model.NotificationConfig) (*model.NotificationConfig, error) {
	err := r.pool.QueryRow(ctx, `
		INSERT INTO notification_configs
			(type, provider, host, port, username, password, from_address, api_key, api_secret, sender_id, enabled)
		VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11)
		ON CONFLICT (type) DO UPDATE SET
			provider     = EXCLUDED.provider,
			host         = EXCLUDED.host,
			port         = EXCLUDED.port,
			username     = EXCLUDED.username,
			password     = EXCLUDED.password,
			from_address = EXCLUDED.from_address,
			api_key      = EXCLUDED.api_key,
			api_secret   = EXCLUDED.api_secret,
			sender_id    = EXCLUDED.sender_id,
			enabled      = EXCLUDED.enabled
		RETURNING id`,
		c.Type, c.Provider, c.Host, c.Port, c.Username, c.Password,
		c.FromAddress, c.APIKey, c.APISecret, c.SenderID, c.Enabled,
	).Scan(&c.ID)
	if err != nil {
		return nil, fmt.Errorf("upsert config: %w", err)
	}
	return c, nil
}

// ---------- NotificationLog ----------

// InsertLog creates a new notification log entry.
func (r *Repository) InsertLog(ctx context.Context, l *model.NotificationLog) error {
	return r.pool.QueryRow(ctx, `
		INSERT INTO notification_logs
			(service_name, type, recipient, subject, body, status, error_message, sent_at)
		VALUES ($1,$2,$3,$4,$5,$6,$7, NOW())
		RETURNING id, sent_at`,
		l.ServiceName, l.Type, l.Recipient, l.Subject, l.Body, l.Status, l.ErrorMessage,
	).Scan(&l.ID, &l.SentAt)
}

// ListLogs returns paginated notification logs ordered by sent_at descending.
func (r *Repository) ListLogs(ctx context.Context, page, size int) ([]model.NotificationLog, int64, error) {
	var total int64
	err := r.pool.QueryRow(ctx, `SELECT COUNT(*) FROM notification_logs`).Scan(&total)
	if err != nil {
		return nil, 0, fmt.Errorf("count logs: %w", err)
	}

	rows, err := r.pool.Query(ctx, `
		SELECT id, service_name, type, recipient, subject, body, status, error_message, sent_at
		FROM notification_logs
		ORDER BY sent_at DESC
		LIMIT $1 OFFSET $2`, size, page*size)
	if err != nil {
		return nil, 0, fmt.Errorf("list logs: %w", err)
	}
	defer rows.Close()

	var logs []model.NotificationLog
	for rows.Next() {
		var l model.NotificationLog
		if err := rows.Scan(
			&l.ID, &l.ServiceName, &l.Type, &l.Recipient, &l.Subject,
			&l.Body, &l.Status, &l.ErrorMessage, &l.SentAt,
		); err != nil {
			return nil, 0, fmt.Errorf("scan log: %w", err)
		}
		logs = append(logs, l)
	}
	return logs, total, nil
}

// FindLogsByServiceName returns logs for a given service.
func (r *Repository) FindLogsByServiceName(ctx context.Context, serviceName string) ([]model.NotificationLog, error) {
	return r.queryLogs(ctx, `
		SELECT id, service_name, type, recipient, subject, body, status, error_message, sent_at
		FROM notification_logs WHERE service_name = $1 ORDER BY sent_at DESC`, serviceName)
}

// FindLogsByRecipient returns logs for a given recipient.
func (r *Repository) FindLogsByRecipient(ctx context.Context, recipient string) ([]model.NotificationLog, error) {
	return r.queryLogs(ctx, `
		SELECT id, service_name, type, recipient, subject, body, status, error_message, sent_at
		FROM notification_logs WHERE recipient = $1 ORDER BY sent_at DESC`, recipient)
}

// FindLogsByType returns logs for a given notification type.
func (r *Repository) FindLogsByType(ctx context.Context, notifType string) ([]model.NotificationLog, error) {
	return r.queryLogs(ctx, `
		SELECT id, service_name, type, recipient, subject, body, status, error_message, sent_at
		FROM notification_logs WHERE type = $1 ORDER BY sent_at DESC`, notifType)
}

func (r *Repository) queryLogs(ctx context.Context, query string, args ...any) ([]model.NotificationLog, error) {
	rows, err := r.pool.Query(ctx, query, args...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var logs []model.NotificationLog
	for rows.Next() {
		var l model.NotificationLog
		if err := rows.Scan(
			&l.ID, &l.ServiceName, &l.Type, &l.Recipient, &l.Subject,
			&l.Body, &l.Status, &l.ErrorMessage, &l.SentAt,
		); err != nil {
			return nil, err
		}
		logs = append(logs, l)
	}
	return logs, nil
}
