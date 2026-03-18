package client

import (
	"context"
	"fmt"
	"math"

	"go.uber.org/zap"

	"github.com/athena-lms/go-services/internal/common/httputil"
)

// CreditScoreResult holds the credit score and band for a customer.
type CreditScoreResult struct {
	Score int    `json:"score"`
	Band  string `json:"band"`
}

// ScoringClient fetches credit scores from the AI scoring service.
type ScoringClient struct {
	baseURL string
	client  *httputil.ServiceClient
	logger  *zap.Logger
}

// NewScoringClient creates a new ScoringClient.
func NewScoringClient(baseURL, serviceKey string, logger *zap.Logger) *ScoringClient {
	return &ScoringClient{
		baseURL: baseURL,
		client:  httputil.NewServiceClient(serviceKey),
		logger:  logger,
	}
}

// GetLatestScore fetches the latest credit score for a customer.
// Falls back to a deterministic mock if the AI scoring service is unavailable.
func (s *ScoringClient) GetLatestScore(ctx context.Context, customerID string) CreditScoreResult {
	url := fmt.Sprintf("%s/api/v1/scoring/customers/%s/latest", s.baseURL, customerID)

	var resp struct {
		FinalScore *int    `json:"finalScore"`
		ScoreBand  *string `json:"scoreBand"`
	}

	if err := s.client.Get(ctx, url, &resp); err != nil {
		s.logger.Warn("AI scoring unavailable, using mock",
			zap.String("customerId", customerID),
			zap.Error(err))
		return generateMockScore(customerID)
	}

	if resp.FinalScore != nil && resp.ScoreBand != nil {
		s.logger.Info("Got credit score",
			zap.String("customerId", customerID),
			zap.Int("score", *resp.FinalScore),
			zap.String("band", *resp.ScoreBand))
		return CreditScoreResult{Score: *resp.FinalScore, Band: *resp.ScoreBand}
	}

	s.logger.Warn("Incomplete scoring response, using mock", zap.String("customerId", customerID))
	return generateMockScore(customerID)
}

// generateMockScore produces a deterministic score from the customer ID hash.
func generateMockScore(customerID string) CreditScoreResult {
	seed := int64(0)
	for _, c := range customerID {
		seed = seed*31 + int64(c)
	}
	seed = int64(math.Abs(float64(seed)))

	baseScore := int(500 + (seed % 350))
	finalScore := baseScore
	if finalScore < 300 {
		finalScore = 300
	}
	if finalScore > 900 {
		finalScore = 900
	}

	var band string
	switch {
	case finalScore >= 750:
		band = "A"
	case finalScore >= 650:
		band = "B"
	case finalScore >= 550:
		band = "C"
	default:
		band = "D"
	}

	return CreditScoreResult{Score: finalScore, Band: band}
}
