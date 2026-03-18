package httputil

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"time"
)

// ContextExtractor extracts service auth context from request context.
// Set by the auth package at init time to avoid import cycles.
var ContextExtractor func(ctx context.Context) (tenantID, userID string)

// ServiceClient is an HTTP client for service-to-service calls.
// It automatically adds the internal service key and tenant context.
type ServiceClient struct {
	client     *http.Client
	serviceKey string
}

// NewServiceClient creates a new service-to-service HTTP client.
func NewServiceClient(serviceKey string) *ServiceClient {
	return &ServiceClient{
		client: &http.Client{
			Timeout: 30 * time.Second,
		},
		serviceKey: serviceKey,
	}
}

// Get performs a GET request to the given URL, injecting service auth headers.
func (c *ServiceClient) Get(ctx context.Context, url string, result any) error {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, url, nil)
	if err != nil {
		return fmt.Errorf("create request: %w", err)
	}
	c.setHeaders(ctx, req)

	resp, err := c.client.Do(req)
	if err != nil {
		return fmt.Errorf("do request: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode >= 400 {
		body, _ := io.ReadAll(resp.Body)
		return fmt.Errorf("service returned %d: %s", resp.StatusCode, string(body))
	}

	if result != nil {
		return json.NewDecoder(resp.Body).Decode(result)
	}
	return nil
}

// Post performs a POST request with a JSON body.
func (c *ServiceClient) Post(ctx context.Context, url string, body, result any) error {
	return c.doJSON(ctx, http.MethodPost, url, body, result)
}

// Put performs a PUT request with a JSON body.
func (c *ServiceClient) Put(ctx context.Context, url string, body, result any) error {
	return c.doJSON(ctx, http.MethodPut, url, body, result)
}

func (c *ServiceClient) doJSON(ctx context.Context, method, url string, body, result any) error {
	var bodyReader io.Reader
	if body != nil {
		b, err := json.Marshal(body)
		if err != nil {
			return fmt.Errorf("marshal body: %w", err)
		}
		bodyReader = bytes.NewReader(b)
	}

	req, err := http.NewRequestWithContext(ctx, method, url, bodyReader)
	if err != nil {
		return fmt.Errorf("create request: %w", err)
	}
	req.Header.Set("Content-Type", "application/json")
	c.setHeaders(ctx, req)

	resp, err := c.client.Do(req)
	if err != nil {
		return fmt.Errorf("do request: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode >= 400 {
		respBody, _ := io.ReadAll(resp.Body)
		return fmt.Errorf("service returned %d: %s", resp.StatusCode, string(respBody))
	}

	if result != nil {
		return json.NewDecoder(resp.Body).Decode(result)
	}
	return nil
}

func (c *ServiceClient) setHeaders(ctx context.Context, req *http.Request) {
	if c.serviceKey != "" {
		req.Header.Set("X-Service-Key", c.serviceKey)
	}
	if ContextExtractor != nil {
		tenantID, userID := ContextExtractor(ctx)
		if tenantID != "" {
			req.Header.Set("X-Service-Tenant", tenantID)
		}
		if userID != "" {
			req.Header.Set("X-Service-User", userID)
		}
	}
}

func init() {
	// Default no-op extractor. The auth package registers the real one.
	if ContextExtractor == nil {
		ContextExtractor = func(ctx context.Context) (string, string) { return "", "" }
	}
}
