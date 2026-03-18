package model

import (
	"time"

	"github.com/google/uuid"
)

// MediaCategory represents the category of a media file.
type MediaCategory string

const (
	MediaCategoryCustomerDocument MediaCategory = "CUSTOMER_DOCUMENT"
	MediaCategoryUserProfile      MediaCategory = "USER_PROFILE"
	MediaCategoryFinancial        MediaCategory = "FINANCIAL"
	MediaCategorySystem           MediaCategory = "SYSTEM"
	MediaCategoryOther            MediaCategory = "OTHER"
)

// ValidMediaCategory checks if the given string is a valid MediaCategory.
func ValidMediaCategory(s string) bool {
	switch MediaCategory(s) {
	case MediaCategoryCustomerDocument, MediaCategoryUserProfile,
		MediaCategoryFinancial, MediaCategorySystem, MediaCategoryOther:
		return true
	}
	return false
}

// MediaType represents the type of a media file.
type MediaType string

const (
	MediaTypeIDFront        MediaType = "ID_FRONT"
	MediaTypeIDBack         MediaType = "ID_BACK"
	MediaTypePassport       MediaType = "PASSPORT"
	MediaTypeSelfie         MediaType = "SELFIE"
	MediaTypeProofOfAddress MediaType = "PROOF_OF_ADDRESS"
	MediaTypeProfilePicture MediaType = "PROFILE_PICTURE"
	MediaTypeSignature      MediaType = "SIGNATURE"
	MediaTypeReceipt        MediaType = "RECEIPT"
	MediaTypeInvoice        MediaType = "INVOICE"
	MediaTypeContract       MediaType = "CONTRACT"
	MediaTypeOther          MediaType = "OTHER"
)

// ValidMediaType checks if the given string is a valid MediaType.
func ValidMediaType(s string) bool {
	switch MediaType(s) {
	case MediaTypeIDFront, MediaTypeIDBack, MediaTypePassport, MediaTypeSelfie,
		MediaTypeProofOfAddress, MediaTypeProfilePicture, MediaTypeSignature,
		MediaTypeReceipt, MediaTypeInvoice, MediaTypeContract, MediaTypeOther:
		return true
	}
	return false
}

// MediaStatus represents the status of a media file.
type MediaStatus string

const (
	MediaStatusActive   MediaStatus = "ACTIVE"
	MediaStatusArchived MediaStatus = "ARCHIVED"
	MediaStatusDeleted  MediaStatus = "DELETED"
)

// ValidMediaStatus checks if the given string is a valid MediaStatus.
func ValidMediaStatus(s string) bool {
	switch MediaStatus(s) {
	case MediaStatusActive, MediaStatusArchived, MediaStatusDeleted:
		return true
	}
	return false
}

// Media represents a media file entity stored in the media_files table.
type Media struct {
	ID               uuid.UUID     `json:"id"`
	TenantID         string        `json:"tenantId"`
	ReferenceID      *uuid.UUID    `json:"referenceId,omitempty"`
	CustomerID       *string       `json:"customerId,omitempty"`
	Category         MediaCategory `json:"category"`
	MediaType        MediaType     `json:"mediaType"`
	OriginalFilename string        `json:"originalFilename"`
	StoredFilename   string        `json:"storedFilename"`
	ContentType      string        `json:"contentType"`
	FileSize         *int64        `json:"fileSize,omitempty"`
	UploadedBy       *string       `json:"uploadedBy,omitempty"`
	ServiceName      *string       `json:"serviceName,omitempty"`
	Channel          *string       `json:"channel,omitempty"`
	Tags             *string       `json:"tags,omitempty"`
	Description      *string       `json:"description,omitempty"`
	IsPublic         bool          `json:"isPublic"`
	Thumbnail        *string       `json:"thumbnail,omitempty"`
	Status           MediaStatus   `json:"status"`
	CreatedAt        time.Time     `json:"createdAt"`
}

// MediaPage represents a paginated list of media files.
type MediaPage struct {
	Content       []Media `json:"content"`
	TotalElements int64   `json:"totalElements"`
	TotalPages    int     `json:"totalPages"`
	Size          int     `json:"size"`
	Number        int     `json:"number"`
}

// MediaStats holds storage and document statistics.
type MediaStats struct {
	TotalSpace      int64            `json:"totalSpace"`
	UsedSpace       int64            `json:"usedSpace"`
	FreeSpace       int64            `json:"freeSpace"`
	UsedPercentage  float64          `json:"usedPercentage"`
	TotalDocuments  int64            `json:"totalDocuments"`
	DocumentsByType map[string]int64 `json:"documentsByType"`
}

// UpdateMetadataRequest represents fields that can be updated on a media record.
type UpdateMetadataRequest struct {
	Description *string      `json:"description,omitempty"`
	Tags        *string      `json:"tags,omitempty"`
	Status      *MediaStatus `json:"status,omitempty"`
}
