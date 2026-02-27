import { apiGet, apiDelete, PageResponse } from "@/lib/api";

const BASE = "/proxy/media/api/v1/media";

export interface MediaFile {
  id: string;
  customerId?: string;
  referenceId?: string;
  category: string;
  mediaType: string;
  originalFilename: string;
  contentType: string;
  fileSize?: number;
  uploadedBy?: string;
  description?: string;
  status: string;
  createdAt: string;
}

export interface MediaStats {
  totalFiles: number;
  totalSizeBytes: number;
  byCategory: Record<string, number>;
}

export const mediaService = {
  async upload(
    file: File,
    customerId?: string,
    referenceId?: string,
    category = "CUSTOMER_DOCUMENT"
  ): Promise<MediaFile> {
    const token = localStorage.getItem("lms_token");
    const formData = new FormData();
    formData.append("file", file);
    if (category) formData.append("category", category);
    if (referenceId) formData.append("referenceId", referenceId);

    const url = customerId
      ? `${BASE}/upload/${encodeURIComponent(customerId)}`
      : `${BASE}/upload`;

    const res = await fetch(url, {
      method: "POST",
      headers: token ? { Authorization: `Bearer ${token}` } : {},
      body: formData,
    });
    if (!res.ok) throw new Error(`Upload failed: ${res.status}`);
    return res.json();
  },

  async listByCustomer(customerId: string): Promise<MediaFile[]> {
    const result = await apiGet<MediaFile[]>(`${BASE}/customer/${encodeURIComponent(customerId)}`);
    return result.data ?? [];
  },

  async listByReference(referenceId: string): Promise<MediaFile[]> {
    const result = await apiGet<MediaFile[]>(`${BASE}/reference/${encodeURIComponent(referenceId)}`);
    return result.data ?? [];
  },

  downloadUrl(mediaId: string): string {
    return `${BASE}/download/${mediaId}`;
  },

  async deleteFile(mediaId: string): Promise<void> {
    await apiDelete(`${BASE}/${mediaId}`);
  },

  async getStats(): Promise<MediaStats | null> {
    const result = await apiGet<MediaStats>(`${BASE}/stats`);
    return result.data ?? null;
  },
};
