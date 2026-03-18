package auth

import "context"

type contextKey string

const (
	tenantIDKey    contextKey = "tenantId"
	userIDKey      contextKey = "userId"
	customerIDKey  contextKey = "customerId"
	customerStrKey contextKey = "customerIdStr"
	rolesKey       contextKey = "roles"
)

// WithTenantID returns a new context with the tenant ID set.
func WithTenantID(ctx context.Context, tenantID string) context.Context {
	return context.WithValue(ctx, tenantIDKey, tenantID)
}

// TenantIDFromContext extracts the tenant ID from context.
func TenantIDFromContext(ctx context.Context) string {
	if v, ok := ctx.Value(tenantIDKey).(string); ok {
		return v
	}
	return ""
}

// TenantIDOrDefault returns the tenant ID or "default" if not set.
func TenantIDOrDefault(ctx context.Context) string {
	tid := TenantIDFromContext(ctx)
	if tid == "" {
		return "default"
	}
	return tid
}

// WithUserID returns a new context with the user ID set.
func WithUserID(ctx context.Context, userID string) context.Context {
	return context.WithValue(ctx, userIDKey, userID)
}

// UserIDFromContext extracts the user ID from context.
func UserIDFromContext(ctx context.Context) string {
	if v, ok := ctx.Value(userIDKey).(string); ok {
		return v
	}
	return ""
}

// WithCustomerID returns a new context with the numeric customer ID set.
func WithCustomerID(ctx context.Context, customerID int64) context.Context {
	return context.WithValue(ctx, customerIDKey, customerID)
}

// CustomerIDFromContext extracts the numeric customer ID from context.
// Returns 0, false if not present.
func CustomerIDFromContext(ctx context.Context) (int64, bool) {
	if v, ok := ctx.Value(customerIDKey).(int64); ok {
		return v, true
	}
	return 0, false
}

// WithCustomerIDStr returns a new context with the string customer ID set.
func WithCustomerIDStr(ctx context.Context, customerIDStr string) context.Context {
	return context.WithValue(ctx, customerStrKey, customerIDStr)
}

// CustomerIDStrFromContext extracts the string customer ID from context.
func CustomerIDStrFromContext(ctx context.Context) string {
	if v, ok := ctx.Value(customerStrKey).(string); ok {
		return v
	}
	return ""
}

// WithRoles returns a new context with the user roles set.
func WithRoles(ctx context.Context, roles []string) context.Context {
	return context.WithValue(ctx, rolesKey, roles)
}

// RolesFromContext extracts the user roles from context.
func RolesFromContext(ctx context.Context) []string {
	if v, ok := ctx.Value(rolesKey).([]string); ok {
		return v
	}
	return nil
}
