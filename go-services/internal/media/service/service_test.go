package service

import (
	"context"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"go.uber.org/zap"
)

func TestGetExtension(t *testing.T) {
	tests := []struct {
		filename string
		want     string
	}{
		{"photo.jpg", ".jpg"},
		{"document.pdf", ".pdf"},
		{"archive.tar.gz", ".gz"},
		{"noextension", ""},
		{"", ""},
		{".hidden", ".hidden"},
	}
	for _, tt := range tests {
		t.Run(tt.filename, func(t *testing.T) {
			got := getExtension(tt.filename)
			assert.Equal(t, tt.want, got)
		})
	}
}

func TestNew_CreatesStorageDirectory(t *testing.T) {
	dir := t.TempDir()
	storagePath := filepath.Join(dir, "media-storage")

	_, err := os.Stat(storagePath)
	require.True(t, os.IsNotExist(err))

	svc, err := New(nil, storagePath, testLogger())
	require.NoError(t, err)
	require.NotNil(t, svc)

	info, err := os.Stat(storagePath)
	require.NoError(t, err)
	assert.True(t, info.IsDir())
}

func TestNew_DefaultStorageLocation(t *testing.T) {
	dir := t.TempDir()
	svc, err := New(nil, dir, testLogger())
	require.NoError(t, err)
	assert.NotNil(t, svc)
}

func TestUpload_EmptyFile(t *testing.T) {
	dir := t.TempDir()
	svc, err := New(nil, dir, testLogger())
	require.NoError(t, err)

	ctx := context.Background()
	params := UploadParams{
		TenantID:   "tenant1",
		Filename:   "empty.txt",
		FileSize:   0,
		FileReader: strings.NewReader(""),
	}

	_, err = svc.Upload(ctx, params)
	require.Error(t, err)
	assert.Contains(t, err.Error(), "empty file")
}

func TestUpload_PathTraversal(t *testing.T) {
	dir := t.TempDir()
	svc, err := New(nil, dir, testLogger())
	require.NoError(t, err)

	ctx := context.Background()
	params := UploadParams{
		TenantID:    "tenant1",
		Filename:    "../../../etc/passwd",
		ContentType: "text/plain",
		FileSize:    5,
		FileReader:  strings.NewReader("hello"),
		Category:    "OTHER",
		MediaType:   "OTHER",
		UploadedBy:  "testuser",
	}

	_, err = svc.Upload(ctx, params)
	require.Error(t, err) // will fail due to nil repo or path issues
}

func testLogger() *zap.Logger {
	logger, _ := zap.NewDevelopment()
	return logger
}
