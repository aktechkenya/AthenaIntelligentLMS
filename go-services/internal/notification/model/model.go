package model

import "time"

// NotificationConfig represents a notification channel configuration (EMAIL or SMS).
type NotificationConfig struct {
	ID          int64   `json:"id"`
	Type        string  `json:"type"`        // EMAIL, SMS
	Provider    *string `json:"provider"`    // SMTP, AFRICAS_TALKING
	Host        *string `json:"host"`
	Port        *int    `json:"port"`
	Username    *string `json:"username"`
	Password    *string `json:"password"`
	FromAddress *string `json:"fromAddress"`
	APIKey      *string `json:"apiKey"`
	APISecret   *string `json:"apiSecret"`
	SenderID    *string `json:"senderId"`
	Enabled     bool    `json:"enabled"`
}

// NotificationLog records every notification send attempt.
type NotificationLog struct {
	ID           int64     `json:"id"`
	ServiceName  string    `json:"serviceName"`
	Type         string    `json:"type"`      // EMAIL, SMS
	Recipient    string    `json:"recipient"`
	Subject      string    `json:"subject"`
	Body         string    `json:"body"`
	Status       string    `json:"status"`    // SENT, FAILED, SKIPPED
	ErrorMessage string    `json:"errorMessage,omitempty"`
	SentAt       time.Time `json:"sentAt"`
}

// NotificationRequest is the DTO for manual send requests.
type NotificationRequest struct {
	ServiceName string            `json:"serviceName"`
	Type        string            `json:"type"` // EMAIL, SMS, PUSH
	Recipient   string            `json:"recipient"`
	Subject     string            `json:"subject"`
	Message     string            `json:"message"`
	Metadata    map[string]any    `json:"metadata,omitempty"`
}
