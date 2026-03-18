package db

import (
	"fmt"

	"github.com/golang-migrate/migrate/v4"
	_ "github.com/golang-migrate/migrate/v4/database/postgres"
	_ "github.com/golang-migrate/migrate/v4/source/file"
	"go.uber.org/zap"
)

// RunMigrations applies database migrations from the given directory.
// migrationsPath should be "file://migrations/service-name".
func RunMigrations(dsn, migrationsPath string, logger *zap.Logger) error {
	m, err := migrate.New(migrationsPath, dsn)
	if err != nil {
		return fmt.Errorf("create migrator: %w", err)
	}
	defer m.Close()

	if err := m.Up(); err != nil && err != migrate.ErrNoChange {
		return fmt.Errorf("run migrations: %w", err)
	}

	version, dirty, _ := m.Version()
	logger.Info("Migrations applied",
		zap.Uint("version", version),
		zap.Bool("dirty", dirty),
	)

	return nil
}
