package scheduler

import (
	"context"
	"time"

	"github.com/robfig/cron/v3"
	"go.uber.org/zap"

	"github.com/athena-lms/go-services/internal/collections/model"
	"github.com/athena-lms/go-services/internal/collections/repository"
)

// PtpCheckScheduler marks expired promises to pay as BROKEN.
// Runs daily at 06:00 UTC, matching the Java @Scheduled(cron = "0 0 6 * * *").
type PtpCheckScheduler struct {
	ptpRepo *repository.PtpRepository
	logger  *zap.Logger
	cron    *cron.Cron
}

// NewPtpCheckScheduler creates a new scheduler.
func NewPtpCheckScheduler(ptpRepo *repository.PtpRepository, logger *zap.Logger) *PtpCheckScheduler {
	return &PtpCheckScheduler{
		ptpRepo: ptpRepo,
		logger:  logger,
	}
}

// Start starts the cron scheduler.
func (s *PtpCheckScheduler) Start() {
	s.cron = cron.New(cron.WithLocation(time.UTC))
	_, err := s.cron.AddFunc("0 6 * * *", s.markExpiredPtpsAsBroken)
	if err != nil {
		s.logger.Error("Failed to schedule PTP check", zap.Error(err))
		return
	}
	s.cron.Start()
	s.logger.Info("PTP check scheduler started (daily at 06:00 UTC)")
}

// Stop stops the cron scheduler.
func (s *PtpCheckScheduler) Stop() {
	if s.cron != nil {
		s.cron.Stop()
		s.logger.Info("PTP check scheduler stopped")
	}
}

func (s *PtpCheckScheduler) markExpiredPtpsAsBroken() {
	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()

	today := time.Now().UTC().Truncate(24 * time.Hour)
	expiredPtps, err := s.ptpRepo.FindByStatusAndPromiseDateBefore(ctx, model.PtpStatusPending, today)
	if err != nil {
		s.logger.Error("PTP check: failed to find expired promises", zap.Error(err))
		return
	}

	if len(expiredPtps) == 0 {
		s.logger.Debug("PTP check: no expired promises found")
		return
	}

	now := time.Now().UTC()
	count := 0
	for _, ptp := range expiredPtps {
		ptp.Status = model.PtpStatusBroken
		ptp.BrokenAt = &now
		if _, err := s.ptpRepo.Save(ctx, ptp); err != nil {
			s.logger.Error("PTP check: failed to update promise",
				zap.String("ptpId", ptp.ID.String()),
				zap.Error(err),
			)
			continue
		}
		count++
	}

	s.logger.Info("PTP check: marked expired promises as BROKEN", zap.Int("count", count))
}
