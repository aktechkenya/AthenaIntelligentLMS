package client

import (
	"encoding/json"
	"fmt"
	"net/http"
	"time"

	"go.uber.org/zap"
)

// CustomerClient resolves customer email addresses by calling the account-service.
type CustomerClient struct {
	baseURL    string
	serviceKey string
	httpClient *http.Client
	logger     *zap.Logger
}

// NewCustomerClient creates a new CustomerClient.
func NewCustomerClient(baseURL, serviceKey string, logger *zap.Logger) *CustomerClient {
	return &CustomerClient{
		baseURL:    baseURL,
		serviceKey: serviceKey,
		httpClient: &http.Client{Timeout: 10 * time.Second},
		logger:     logger,
	}
}

// ResolveEmail looks up a customer's email from the account-service.
// Falls back to "noreply@athena.lms" on any failure.
func (c *CustomerClient) ResolveEmail(customerID, tenantID string) string {
	if customerID == "" {
		return "noreply@athena.lms"
	}

	url := fmt.Sprintf("%s/api/v1/customers/by-customer-id/%s", c.baseURL, customerID)
	req, err := http.NewRequest(http.MethodGet, url, nil)
	if err != nil {
		c.logger.Warn("Failed to create request for customer lookup", zap.Error(err))
		return "noreply@athena.lms"
	}

	req.Header.Set("X-Service-Key", c.serviceKey)
	if tenantID != "" {
		req.Header.Set("X-Service-Tenant", tenantID)
	} else {
		req.Header.Set("X-Service-Tenant", "default")
	}
	req.Header.Set("X-Service-User", "notification-service")

	resp, err := c.httpClient.Do(req)
	if err != nil {
		c.logger.Warn("Could not resolve email for customer",
			zap.String("customerId", customerID), zap.Error(err))
		return "noreply@athena.lms"
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		c.logger.Warn("Account-service returned non-200",
			zap.String("customerId", customerID), zap.Int("status", resp.StatusCode))
		return "noreply@athena.lms"
	}

	var body map[string]any
	if err := json.NewDecoder(resp.Body).Decode(&body); err != nil {
		c.logger.Warn("Failed to decode customer response", zap.Error(err))
		return "noreply@athena.lms"
	}

	if email, ok := body["email"].(string); ok && email != "" {
		return email
	}
	return "noreply@athena.lms"
}
