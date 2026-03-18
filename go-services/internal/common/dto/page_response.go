package dto

// PageResponse is the paginated response shape matching Java PageResponse<T>.
// JSON field names match exactly for frontend compatibility.
type PageResponse struct {
	Content       any   `json:"content"`
	Page          int   `json:"page"`
	Size          int   `json:"size"`
	TotalElements int64 `json:"totalElements"`
	TotalPages    int   `json:"totalPages"`
	Last          bool  `json:"last"`
}

// NewPageResponse creates a PageResponse from query results.
func NewPageResponse(content any, page, size int, totalElements int64) PageResponse {
	totalPages := 0
	if size > 0 {
		totalPages = int((totalElements + int64(size) - 1) / int64(size))
	}
	return PageResponse{
		Content:       content,
		Page:          page,
		Size:          size,
		TotalElements: totalElements,
		TotalPages:    totalPages,
		Last:          page >= totalPages-1,
	}
}
