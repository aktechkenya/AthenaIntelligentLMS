package scheduler

import (
	"context"
	"time"

	"go.uber.org/zap"

	"github.com/athena-lms/go-services/internal/management/service"
)

// DpdRefreshScheduler runs daily DPD calculation for all active loans.
type DpdRefreshScheduler struct {
	svc    *service.Service
	logger *zap.Logger
}

// NewDpdRefreshScheduler creates a new DpdRefreshScheduler.
func NewDpdRefreshScheduler(svc *service.Service, logger *zap.Logger) *DpdRefreshScheduler {
	return &DpdRefreshScheduler{svc: svc, logger: logger}
}

// Start begins the scheduler. It runs the DPD refresh daily at 01:00 AM.
// Blocks until ctx is cancelled.
func (s *DpdRefreshScheduler) Start(ctx context.Context) {
	s.logger.Info("DPD refresh scheduler started")

	// Run once immediately on startup
	s.run(ctx)

	for {
		nextRun := nextRunTime()
		timer := time.NewTimer(time.Until(nextRun))

		select {
		case <-ctx.Done():
			timer.Stop()
			s.logger.Info("DPD refresh scheduler stopped")
			return
		case <-timer.C:
			s.run(ctx)
		}
	}
}

func (s *DpdRefreshScheduler) run(ctx context.Context) {
	s.logger.Info("Starting daily DPD refresh job")
	defer func() {
		if r := recover(); r != nil {
			s.logger.Error("DPD refresh job panicked", zap.Any("recover", r))
		}
	}()

	s.svc.RefreshAllDpd(ctx)
	s.logger.Info("DPD refresh job completed")
}

// nextRunTime returns the next 01:00 AM UTC.
func nextRunTime() time.Time {
	now := time.Now().UTC()
	next := time.Date(now.Year(), now.Month(), now.Day(), 1, 0, 0, 0, time.UTC)
	if !next.After(now) {
		next = next.Add(24 * time.Hour)
	}
	return next
}
