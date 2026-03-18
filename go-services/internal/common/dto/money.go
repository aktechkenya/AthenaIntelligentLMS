package dto

import (
	"encoding/json"
	"fmt"

	"github.com/shopspring/decimal"
)

// Money wraps decimal.Decimal to serialize as a JSON number instead of string.
// shopspring/decimal serializes as "123.45" (string) by default.
// The frontend expects 123.45 (number) for toLocaleString() formatting.
type Money struct {
	decimal.Decimal
}

// NewMoney creates a Money from a decimal.Decimal.
func NewMoney(d decimal.Decimal) Money {
	return Money{Decimal: d}
}

// MarshalJSON serializes Money as a JSON number.
func (m Money) MarshalJSON() ([]byte, error) {
	return []byte(m.Decimal.StringFixed(2)), nil
}

// UnmarshalJSON deserializes a JSON number or string into Money.
func (m *Money) UnmarshalJSON(data []byte) error {
	// Try as number first
	var f float64
	if err := json.Unmarshal(data, &f); err == nil {
		m.Decimal = decimal.NewFromFloat(f)
		return nil
	}
	// Try as string
	var s string
	if err := json.Unmarshal(data, &s); err == nil {
		d, err := decimal.NewFromString(s)
		if err != nil {
			return fmt.Errorf("invalid money value: %s", s)
		}
		m.Decimal = d
		return nil
	}
	return fmt.Errorf("cannot unmarshal %s into Money", string(data))
}
