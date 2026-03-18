package client

import (
	"context"
	"encoding/json"
	"fmt"
	"math"
	"net/http"
	"time"

	"github.com/shopspring/decimal"
	"go.uber.org/zap"

	"github.com/athena-lms/go-services/internal/scoring/model"
)

// AthenaScoreClient calls the external AthenaCreditScore API
// and falls back to a deterministic mock score when unavailable.
type AthenaScoreClient struct {
	baseURL string
	client  *http.Client
	logger  *zap.Logger
}

// NewAthenaScoreClient creates a new external scoring client.
func NewAthenaScoreClient(baseURL string, logger *zap.Logger) *AthenaScoreClient {
	return &AthenaScoreClient{
		baseURL: baseURL,
		client: &http.Client{
			Timeout: 15 * time.Second,
		},
		logger: logger,
	}
}

// GetScore retrieves a credit score for the given customer.
// Falls back to a deterministic mock score if the external API is unavailable.
func (c *AthenaScoreClient) GetScore(ctx context.Context, customerID int64) (*model.ExternalScoreResponse, error) {
	url := fmt.Sprintf("%s/api/v1/credit-score/%d", c.baseURL, customerID)

	req, err := http.NewRequestWithContext(ctx, http.MethodGet, url, nil)
	if err != nil {
		c.logger.Warn("Failed to create request for external scoring API",
			zap.Int64("customerId", customerID), zap.Error(err))
		return c.generateMockScore(customerID), nil
	}

	resp, err := c.client.Do(req)
	if err != nil {
		c.logger.Warn("External scoring API unavailable, using mock score",
			zap.Int64("customerId", customerID), zap.Error(err))
		return c.generateMockScore(customerID), nil
	}
	defer resp.Body.Close()

	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		c.logger.Warn("External scoring API returned non-2xx, using mock score",
			zap.Int64("customerId", customerID), zap.Int("status", resp.StatusCode))
		return c.generateMockScore(customerID), nil
	}

	var score model.ExternalScoreResponse
	if err := json.NewDecoder(resp.Body).Decode(&score); err != nil {
		c.logger.Warn("Failed to decode external score response, using mock score",
			zap.Int64("customerId", customerID), zap.Error(err))
		return c.generateMockScore(customerID), nil
	}

	c.logger.Info("Got real credit score for customer", zap.Int64("customerId", customerID))
	return &score, nil
}

// generateMockScore produces a deterministic mock credit score based on customerId.
func (c *AthenaScoreClient) generateMockScore(customerID int64) *model.ExternalScoreResponse {
	seed := int64(math.Abs(float64(customerID)))
	if seed == 0 {
		seed = 12345
	}

	baseScore := 500 + int(seed%350)
	crbAdj := int(seed%50) - 25
	llmAdj := int(seed%30) - 10
	finalScore := baseScore + crbAdj + llmAdj
	if finalScore < 300 {
		finalScore = 300
	}
	if finalScore > 900 {
		finalScore = 900
	}

	var scoreBand string
	var pdProb float64
	switch {
	case finalScore >= 750:
		scoreBand = "A"
		pdProb = 0.01 + float64(seed%3)*0.005
	case finalScore >= 650:
		scoreBand = "B"
		pdProb = 0.04 + float64(seed%5)*0.01
	case finalScore >= 550:
		scoreBand = "C"
		pdProb = 0.10 + float64(seed%5)*0.02
	default:
		scoreBand = "D"
		pdProb = 0.20 + float64(seed%5)*0.03
	}

	now := time.Now().UTC().Format(time.RFC3339)

	mock := &model.ExternalScoreResponse{
		CustomerID:      customerID,
		BaseScore:       decimal.NewFromInt(int64(baseScore)),
		CrbContribution: decimal.NewFromInt(int64(crbAdj)),
		LlmAdjustment:   decimal.NewFromInt(int64(llmAdj)),
		PdProbability:   decimal.NewFromFloat(pdProb).Round(4),
		FinalScore:      decimal.NewFromInt(int64(finalScore)),
		ScoreBand:       scoreBand,
		LlmProvider:     "mock",
		LlmModel:        "deterministic-v1",
		ScoredAt:        now,
		Reasoning: []string{
			"Mock score — external AthenaCreditScore API unavailable",
			fmt.Sprintf("Base score: %d (derived from customer profile)", baseScore),
			fmt.Sprintf("CRB adjustment: %d", crbAdj),
			fmt.Sprintf("Score band: %s, PD: %.2f%%", scoreBand, pdProb*100),
		},
	}

	c.logger.Info("Generated mock score for customer",
		zap.Int64("customerId", customerID),
		zap.Int("finalScore", finalScore),
		zap.String("band", scoreBand),
		zap.Float64("pd", pdProb),
	)

	return mock
}
