package auth

import (
	"context"
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestTenantContext(t *testing.T) {
	ctx := context.Background()

	// Empty context returns empty string
	assert.Equal(t, "", TenantIDFromContext(ctx))
	assert.Equal(t, "default", TenantIDOrDefault(ctx))

	// Set tenant
	ctx = WithTenantID(ctx, "tenant1")
	assert.Equal(t, "tenant1", TenantIDFromContext(ctx))
	assert.Equal(t, "tenant1", TenantIDOrDefault(ctx))
}

func TestUserContext(t *testing.T) {
	ctx := context.Background()
	assert.Equal(t, "", UserIDFromContext(ctx))

	ctx = WithUserID(ctx, "admin")
	assert.Equal(t, "admin", UserIDFromContext(ctx))
}

func TestCustomerContext(t *testing.T) {
	ctx := context.Background()

	_, ok := CustomerIDFromContext(ctx)
	assert.False(t, ok)
	assert.Equal(t, "", CustomerIDStrFromContext(ctx))

	ctx = WithCustomerID(ctx, 42)
	ctx = WithCustomerIDStr(ctx, "42")
	cid, ok := CustomerIDFromContext(ctx)
	assert.True(t, ok)
	assert.Equal(t, int64(42), cid)
	assert.Equal(t, "42", CustomerIDStrFromContext(ctx))
}

func TestRolesContext(t *testing.T) {
	ctx := context.Background()
	assert.Nil(t, RolesFromContext(ctx))

	ctx = WithRoles(ctx, []string{"ADMIN", "USER"})
	assert.Equal(t, []string{"ADMIN", "USER"}, RolesFromContext(ctx))
}
