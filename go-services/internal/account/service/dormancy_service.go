package service

import (
	"context"
	"fmt"

	"go.uber.org/zap"

	"github.com/athena-lms/go-services/internal/account/event"
	"github.com/athena-lms/go-services/internal/account/repository"
)

// DormancyService handles dormancy detection and enforcement.
type DormancyService struct {
	repo      *repository.Repository
	publisher *event.Publisher
	logger    *zap.Logger
}

// NewDormancyService creates a new DormancyService.
func NewDormancyService(repo *repository.Repository, publisher *event.Publisher, logger *zap.Logger) *DormancyService {
	return &DormancyService{repo: repo, publisher: publisher, logger: logger}
}

// DetectDormantAccounts marks accounts as dormant if inactive beyond threshold.
func (s *DormancyService) DetectDormantAccounts(ctx context.Context, tenantID string, thresholdDays int) (int, error) {
	if thresholdDays <= 0 {
		thresholdDays = 365
	}

	accounts, err := s.repo.ListDormancyCandidates(ctx, tenantID, thresholdDays)
	if err != nil {
		return 0, fmt.Errorf("list dormancy candidates: %w", err)
	}

	marked := 0
	for _, account := range accounts {
		if err := s.repo.SetAccountDormant(ctx, account.ID); err != nil {
			s.logger.Warn("Failed to set account dormant",
				zap.String("accountId", account.ID.String()),
				zap.Error(err))
			continue
		}
		marked++
		s.logger.Info("Account marked dormant",
			zap.String("accountId", account.ID.String()),
			zap.String("accountNumber", account.AccountNumber))
	}

	s.logger.Info("Dormancy detection completed",
		zap.Int("markedDormant", marked),
		zap.Int("candidates", len(accounts)))

	return marked, nil
}
