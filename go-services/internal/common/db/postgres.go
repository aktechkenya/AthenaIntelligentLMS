package db

import (
	"context"
	"fmt"
	"time"

	"github.com/jackc/pgx/v5/pgxpool"
	"go.uber.org/zap"
)

// NewPool creates a PostgreSQL connection pool with retry.
func NewPool(ctx context.Context, dsn string, logger *zap.Logger) (*pgxpool.Pool, error) {
	config, err := pgxpool.ParseConfig(dsn)
	if err != nil {
		return nil, fmt.Errorf("parse dsn: %w", err)
	}

	config.MaxConns = 20
	config.MinConns = 2
	config.MaxConnLifetime = 30 * time.Minute
	config.MaxConnIdleTime = 5 * time.Minute

	var pool *pgxpool.Pool
	for i := 0; i < 30; i++ {
		pool, err = pgxpool.NewWithConfig(ctx, config)
		if err == nil {
			if err = pool.Ping(ctx); err == nil {
				logger.Info("Connected to PostgreSQL", zap.String("database", config.ConnConfig.Database))
				return pool, nil
			}
			pool.Close()
		}
		logger.Warn("PostgreSQL connection attempt failed, retrying...",
			zap.Int("attempt", i+1),
			zap.Error(err))
		time.Sleep(2 * time.Second)
	}
	return nil, fmt.Errorf("failed to connect to PostgreSQL after 30 attempts: %w", err)
}
