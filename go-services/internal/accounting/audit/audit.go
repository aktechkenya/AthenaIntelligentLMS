package audit

import (
	"context"
	"encoding/json"

	"go.uber.org/zap"

	"github.com/athena-lms/go-services/internal/accounting/model"
	"github.com/athena-lms/go-services/internal/accounting/repository"
	"github.com/athena-lms/go-services/internal/common/auth"
)

// Logger provides financial audit logging.
type Logger struct {
	repo   *repository.Repository
	logger *zap.Logger
}

// New creates a new audit Logger.
func New(repo *repository.Repository, logger *zap.Logger) *Logger {
	return &Logger{repo: repo, logger: logger}
}

// Log records an audit event. It auto-extracts user/tenant/role from context.
func (l *Logger) Log(ctx context.Context, action, entityType, entityID string, details any) {
	tenantID := auth.TenantIDOrDefault(ctx)
	userID := auth.UserIDFromContext(ctx)
	roles := auth.RolesFromContext(ctx)

	var userIDPtr, userRolePtr *string
	if userID != "" {
		userIDPtr = &userID
	}
	if len(roles) > 0 {
		role := roles[0]
		userRolePtr = &role
	}

	var detailsJSON any
	if details != nil {
		b, err := json.Marshal(details)
		if err == nil {
			detailsJSON = b
		}
	}

	entry := &model.FinancialAuditLog{
		TenantID:   tenantID,
		Action:     action,
		EntityType: entityType,
		EntityID:   entityID,
		UserID:     userIDPtr,
		UserRole:   userRolePtr,
		Details:    detailsJSON,
	}

	if err := l.repo.InsertAuditLog(ctx, entry); err != nil {
		l.logger.Error("Failed to write audit log",
			zap.String("action", action),
			zap.String("entityType", entityType),
			zap.String("entityId", entityID),
			zap.Error(err))
	}
}
