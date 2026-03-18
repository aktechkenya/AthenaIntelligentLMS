package service

import (
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestBuildEmailMessage(t *testing.T) {
	msg := buildEmailMessage("from@test.com", "to@test.com", "Test Subject", "Hello World")

	expected := "From: from@test.com\r\nTo: to@test.com\r\nSubject: Test Subject\r\nMIME-Version: 1.0\r\nContent-Type: text/plain; charset=UTF-8\r\n\r\nHello World"
	assert.Equal(t, expected, string(msg))
}

func TestBuildEmailMessage_SpecialCharacters(t *testing.T) {
	msg := buildEmailMessage(
		"noreply@athena.co.ke",
		"customer@example.com",
		"Dispute Received — Athena Credit Score",
		"Dear Customer,\n\nYour dispute has been received.",
	)
	msgStr := string(msg)

	assert.Contains(t, msgStr, "From: noreply@athena.co.ke")
	assert.Contains(t, msgStr, "To: customer@example.com")
	assert.Contains(t, msgStr, "Dispute Received — Athena Credit Score")
	assert.Contains(t, msgStr, "Dear Customer,\n\nYour dispute has been received.")
}

func TestBuildEmailMessage_EmptyBody(t *testing.T) {
	msg := buildEmailMessage("a@b.com", "c@d.com", "Subject", "")
	assert.Contains(t, string(msg), "Subject: Subject\r\n")
	assert.True(t, len(msg) > 0)
}
