package auth

import (
	"crypto/hmac"
	"crypto/sha256"
	"encoding/base64"
	"encoding/json"
	"strings"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

// createTestToken builds a JWT with the given claims using HMAC-SHA256.
// Matches the Java JJWT token format exactly.
func createTestToken(t *testing.T, secret string, claims map[string]any) string {
	t.Helper()

	header := base64URLEncode([]byte(`{"alg":"HS256","typ":"JWT"}`))

	// Add expiration if not set
	if _, ok := claims["exp"]; !ok {
		claims["exp"] = time.Now().Add(time.Hour).Unix()
	}

	claimsJSON, err := json.Marshal(claims)
	require.NoError(t, err)
	payload := base64URLEncode(claimsJSON)

	sigInput := header + "." + payload
	keyBytes, err := base64.StdEncoding.DecodeString(secret)
	require.NoError(t, err)

	mac := hmac.New(sha256.New, keyBytes)
	mac.Write([]byte(sigInput))
	sig := base64URLEncode(mac.Sum(nil))

	return sigInput + "." + sig
}

func base64URLEncode(data []byte) string {
	return strings.TrimRight(base64.URLEncoding.EncodeToString(data), "=")
}

func TestJWTUtil_ParseToken(t *testing.T) {
	// 256-bit key encoded as base64 (matches Java HMAC-SHA key length)
	secret := base64.StdEncoding.EncodeToString([]byte("01234567890123456789012345678901"))

	jwtUtil, err := NewJWTUtil(secret)
	require.NoError(t, err)

	token := createTestToken(t, secret, map[string]any{
		"sub":        "admin",
		"tenantId":   "tenant1",
		"customerId": 42,
		"roles":      []string{"ADMIN", "USER"},
	})

	claims, err := jwtUtil.ParseToken(token)
	require.NoError(t, err)

	assert.Equal(t, "admin", claims.Username)
	assert.Equal(t, "tenant1", claims.TenantID)
	assert.NotNil(t, claims.CustomerID)
	assert.Equal(t, int64(42), *claims.CustomerID)
	assert.Equal(t, "42", claims.CustomerIDStr)
	assert.Equal(t, []string{"ADMIN", "USER"}, claims.Roles)
}

func TestJWTUtil_ParseToken_MobileWalletStringCustomerID(t *testing.T) {
	secret := base64.StdEncoding.EncodeToString([]byte("01234567890123456789012345678901"))

	jwtUtil, err := NewJWTUtil(secret)
	require.NoError(t, err)

	// Mobile wallet tokens use string customer IDs like "MOB-C88EE444"
	token := createTestToken(t, secret, map[string]any{
		"sub":        "mobile-user",
		"tenantId":   "tenant1",
		"customerId": "MOB-C88EE444",
		"roles":      []string{"USER"},
	})

	claims, err := jwtUtil.ParseToken(token)
	require.NoError(t, err)

	assert.Equal(t, "mobile-user", claims.Username)
	assert.Nil(t, claims.CustomerID) // string ID can't be parsed as int64
	assert.Equal(t, "MOB-C88EE444", claims.CustomerIDStr)
}

func TestJWTUtil_ParseToken_TenantFallback(t *testing.T) {
	secret := base64.StdEncoding.EncodeToString([]byte("01234567890123456789012345678901"))

	jwtUtil, err := NewJWTUtil(secret)
	require.NoError(t, err)

	// No tenantId claim — should fall back to subject
	token := createTestToken(t, secret, map[string]any{
		"sub":   "admin",
		"roles": []string{"ADMIN"},
	})

	claims, err := jwtUtil.ParseToken(token)
	require.NoError(t, err)

	assert.Equal(t, "admin", claims.TenantID) // falls back to subject
}

func TestJWTUtil_ParseToken_ExpiredToken(t *testing.T) {
	secret := base64.StdEncoding.EncodeToString([]byte("01234567890123456789012345678901"))

	jwtUtil, err := NewJWTUtil(secret)
	require.NoError(t, err)

	token := createTestToken(t, secret, map[string]any{
		"sub": "admin",
		"exp": time.Now().Add(-time.Hour).Unix(), // expired
	})

	_, err = jwtUtil.ParseToken(token)
	assert.Error(t, err) // should fail validation
}

func TestJWTUtil_ParseToken_InvalidSignature(t *testing.T) {
	secret := base64.StdEncoding.EncodeToString([]byte("01234567890123456789012345678901"))
	wrongSecret := base64.StdEncoding.EncodeToString([]byte("99999999999999999999999999999999"))

	jwtUtil, err := NewJWTUtil(secret)
	require.NoError(t, err)

	// Token signed with wrong key
	token := createTestToken(t, wrongSecret, map[string]any{
		"sub": "admin",
	})

	_, err = jwtUtil.ParseToken(token)
	assert.Error(t, err) // should fail signature validation
}
