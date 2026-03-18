package auth

import (
	"encoding/base64"
	"errors"
	"fmt"
	"strconv"

	"github.com/golang-jwt/jwt/v5"
)

// JWTUtil parses and validates JWT tokens.
// Port of Java JwtUtil.java — same HMAC secret, same claims structure.
type JWTUtil struct {
	signingKey []byte
}

// NewJWTUtil creates a JWTUtil from a base64-encoded HMAC secret.
func NewJWTUtil(base64Secret string) (*JWTUtil, error) {
	key, err := base64.StdEncoding.DecodeString(base64Secret)
	if err != nil {
		return nil, fmt.Errorf("decode jwt secret: %w", err)
	}
	return &JWTUtil{signingKey: key}, nil
}

// Claims holds the extracted JWT claims matching the Java token structure.
type Claims struct {
	Username   string
	TenantID   string
	CustomerID *int64  // nil if not present or not numeric (e.g. mobile wallet string IDs)
	CustomerIDStr string // raw string value of customerId claim
	Roles      []string
}

// ParseToken validates the token signature and extracts claims.
func (j *JWTUtil) ParseToken(tokenString string) (*Claims, error) {
	token, err := jwt.Parse(tokenString, func(token *jwt.Token) (any, error) {
		if _, ok := token.Method.(*jwt.SigningMethodHMAC); !ok {
			return nil, fmt.Errorf("unexpected signing method: %v", token.Header["alg"])
		}
		return j.signingKey, nil
	})
	if err != nil {
		return nil, fmt.Errorf("parse token: %w", err)
	}

	mapClaims, ok := token.Claims.(jwt.MapClaims)
	if !ok || !token.Valid {
		return nil, errors.New("invalid token claims")
	}

	c := &Claims{}

	// Subject = username
	if sub, ok := mapClaims["sub"].(string); ok {
		c.Username = sub
	}

	// TenantID — fall back to subject for single-tenant deployments
	if tid, ok := mapClaims["tenantId"].(string); ok {
		c.TenantID = tid
	} else {
		c.TenantID = c.Username
	}

	// CustomerID — supports both numeric and string IDs (mobile wallet compat)
	if cid, ok := mapClaims["customerId"]; ok && cid != nil {
		cidStr := fmt.Sprintf("%v", cid)
		c.CustomerIDStr = cidStr
		if n, err := strconv.ParseInt(cidStr, 10, 64); err == nil {
			c.CustomerID = &n
		}
	}

	// Roles
	if roles, ok := mapClaims["roles"].([]any); ok {
		for _, r := range roles {
			if s, ok := r.(string); ok {
				c.Roles = append(c.Roles, s)
			}
		}
	}

	return c, nil
}
