import { apiGet, type PageResponse } from "@/lib/api";

export interface CollectionCase {
  id: string;
  loanId: string;
  customerId?: string;
  status: string;
  dpd: number;
  outstandingAmount?: number;
  currency?: string;
  assignedTo?: string;
  lastContactDate?: string;
  nextActionDate?: string;
  stage?: string;
  notes?: string;
  createdAt?: string;
  updatedAt?: string;
}

const BASE = "/proxy/collections/api/v1/collections";

export const collectionsService = {
  async listCases(
    page = 0,
    size = 20,
    status?: string
  ): Promise<PageResponse<CollectionCase>> {
    const params = new URLSearchParams({ page: String(page), size: String(size) });
    if (status) params.set("status", status);
    const result = await apiGet<PageResponse<CollectionCase>>(`${BASE}/cases?${params}`);
    if (result.error || !result.data) {
      throw new Error(result.error ?? "Failed to list collection cases");
    }
    return result.data;
  },
};
