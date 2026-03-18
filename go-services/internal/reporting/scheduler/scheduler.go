package scheduler

import (
	"context"

	"github.com/robfig/cron/v3"
	"go.uber.org/zap"

	"github.com/athena-lms/go-services/internal/reporting/service"
)

// Scheduler runs periodic reporting tasks using cron.
type Scheduler struct {
	cron   *cron.Cron
	svc    *service.Service
	logger *zap.Logger
}

// New creates a new Scheduler. The daily snapshot runs at 01:30.
func New(svc *service.Service, logger *zap.Logger) *Scheduler {
	return &Scheduler{
		cron:   cron.New(),
		svc:    svc,
		logger: logger,
	}
}

// Start registers the cron jobs and starts the scheduler.
// Call Stop() to shut it down.
func (s *Scheduler) Start(ctx context.Context) error {
	_, err := s.cron.AddFunc("30 1 * * *", func() {
		s.generateDailySnapshots(ctx)
	})
	if err != nil {
		return err
	}

	s.cron.Start()
	s.logger.Info("Snapshot scheduler started (daily at 01:30)")
	return nil
}

// Stop gracefully stops the scheduler.
func (s *Scheduler) Stop() {
	s.cron.Stop()
	s.logger.Info("Snapshot scheduler stopped")
}

func (s *Scheduler) generateDailySnapshots(ctx context.Context) {
	s.logger.Info("SnapshotScheduler: starting daily snapshot generation")
	if err := s.svc.GenerateDailySnapshot(ctx, "default"); err != nil {
		s.logger.Error("SnapshotScheduler: error generating daily snapshot", zap.Error(err))
		return
	}
	s.logger.Info("SnapshotScheduler: daily snapshot generation complete")
}
