package model

import (
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestValidAlertType(t *testing.T) {
	valid := []string{
		"LARGE_TRANSACTION", "RAPID_TRANSACTIONS", "UNUSUAL_PATTERN",
		"HIGH_RISK_CUSTOMER", "SANCTIONED_ENTITY", "STRUCTURING",
		"PEP_MATCH", "GEOGRAPHIC_RISK", "OTHER",
	}
	for _, v := range valid {
		assert.True(t, ValidAlertType(v), "expected %q to be valid", v)
	}
	assert.False(t, ValidAlertType("INVALID"))
	assert.False(t, ValidAlertType(""))
}

func TestValidAlertSeverity(t *testing.T) {
	valid := []string{"LOW", "MEDIUM", "HIGH", "CRITICAL"}
	for _, v := range valid {
		assert.True(t, ValidAlertSeverity(v), "expected %q to be valid", v)
	}
	assert.False(t, ValidAlertSeverity("EXTREME"))
	assert.False(t, ValidAlertSeverity(""))
}

func TestValidAlertStatus(t *testing.T) {
	valid := []string{"OPEN", "UNDER_REVIEW", "ESCALATED", "SAR_FILED", "CLOSED_FALSE_POSITIVE", "CLOSED_ACTIONED"}
	for _, v := range valid {
		assert.True(t, ValidAlertStatus(v), "expected %q to be valid", v)
	}
	assert.False(t, ValidAlertStatus("DELETED"))
	assert.False(t, ValidAlertStatus(""))
}

func TestValidKycStatus(t *testing.T) {
	valid := []string{"PENDING", "IN_PROGRESS", "PASSED", "FAILED", "EXPIRED"}
	for _, v := range valid {
		assert.True(t, ValidKycStatus(v), "expected %q to be valid", v)
	}
	assert.False(t, ValidKycStatus("CANCELLED"))
	assert.False(t, ValidKycStatus(""))
}

func TestValidRiskLevel(t *testing.T) {
	valid := []string{"LOW", "MEDIUM", "HIGH", "VERY_HIGH"}
	for _, v := range valid {
		assert.True(t, ValidRiskLevel(v), "expected %q to be valid", v)
	}
	assert.False(t, ValidRiskLevel("EXTREME"))
	assert.False(t, ValidRiskLevel(""))
}

func TestAlertTypeConstants(t *testing.T) {
	assert.Equal(t, AlertType("LARGE_TRANSACTION"), AlertTypeLargeTransaction)
	assert.Equal(t, AlertType("PEP_MATCH"), AlertTypePEPMatch)
	assert.Equal(t, AlertType("OTHER"), AlertTypeOther)
}

func TestAlertSeverityConstants(t *testing.T) {
	assert.Equal(t, AlertSeverity("LOW"), AlertSeverityLow)
	assert.Equal(t, AlertSeverity("CRITICAL"), AlertSeverityCritical)
}

func TestAlertStatusConstants(t *testing.T) {
	assert.Equal(t, AlertStatus("OPEN"), AlertStatusOpen)
	assert.Equal(t, AlertStatus("SAR_FILED"), AlertStatusSARFiled)
	assert.Equal(t, AlertStatus("CLOSED_ACTIONED"), AlertStatusClosedActioned)
}

func TestKycStatusConstants(t *testing.T) {
	assert.Equal(t, KycStatus("PENDING"), KycStatusPending)
	assert.Equal(t, KycStatus("IN_PROGRESS"), KycStatusInProgress)
	assert.Equal(t, KycStatus("PASSED"), KycStatusPassed)
}

func TestRiskLevelConstants(t *testing.T) {
	assert.Equal(t, RiskLevel("LOW"), RiskLevelLow)
	assert.Equal(t, RiskLevel("VERY_HIGH"), RiskLevelVeryHigh)
}
