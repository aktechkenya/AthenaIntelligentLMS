package event

import (
	"context"

	"go.uber.org/zap"

	"github.com/athena-lms/go-services/internal/accounting/model"
	"github.com/athena-lms/go-services/internal/common/event"
)

// Publisher publishes accounting domain events.
type Publisher struct {
	pub    *event.Publisher
	logger *zap.Logger
}

// NewPublisher creates a new accounting event publisher.
func NewPublisher(pub *event.Publisher, logger *zap.Logger) *Publisher {
	return &Publisher{pub: pub, logger: logger}
}

// PublishJournalPosted publishes an "accounting.posted" event after a journal entry is committed.
func (p *Publisher) PublishJournalPosted(ctx context.Context, entry *model.JournalEntry) {
	sourceEvent := ""
	if entry.SourceEvent != nil {
		sourceEvent = *entry.SourceEvent
	}
	sourceID := ""
	if entry.SourceID != nil {
		sourceID = *entry.SourceID
	}

	payload := map[string]any{
		"entryId":     entry.ID.String(),
		"reference":   entry.Reference,
		"entryDate":   entry.EntryDate.Format("2006-01-02"),
		"sourceEvent": sourceEvent,
		"sourceId":    sourceID,
		"totalDebit":  entry.TotalDebit.String(),
		"totalCredit": entry.TotalCredit.String(),
	}

	evt, err := event.NewDomainEvent("accounting.posted", "accounting-service", entry.TenantID, "", payload)
	if err != nil {
		p.logger.Error("Failed to create accounting.posted event", zap.Error(err))
		return
	}

	if err := p.pub.Publish(ctx, evt); err != nil {
		p.logger.Error("Failed to publish accounting.posted event",
			zap.String("entryId", entry.ID.String()),
			zap.Error(err))
		return
	}

	p.logger.Info("Published accounting.posted",
		zap.String("entryId", entry.ID.String()),
		zap.String("reference", entry.Reference))
}
