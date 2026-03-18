package service

import (
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestStrPtr_Empty(t *testing.T) {
	result := strPtr("")
	assert.Nil(t, result)
}

func TestStrPtr_NonEmpty(t *testing.T) {
	result := strPtr("hello")
	assert.NotNil(t, result)
	assert.Equal(t, "hello", *result)
}
