import { apiGet, type PageResponse } from "@/lib/api";

export interface ScoringRequest {
  id: string;
  tenantId?: string;
  customerId: number | string;
  loanApplicationId?: string;
  status?: string;
  triggerEvent?: string;
  requestedAt?: string;
  completedAt?: string;
  errorMessage?: string;
  createdAt?: string;
  // Score result fields (from separate entity, may be joined)
  score?: number;
  scoreBand?: string;
}

// The API returns page/size/totalElements/totalPages/last (not number/first)
interface ScoringPage {
  content: ScoringRequest[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
}

const BASE = "/proxy/scoring/api/v1/scoring";

export const scoringService = {
  async listRequests(
    page = 0,
    size = 20
  ): Promise<PageResponse<ScoringRequest>> {
    const params = new URLSearchParams({ page: String(page), size: String(size) });
    const result = await apiGet<ScoringPage>(`${BASE}/requests?${params}`);
    if (result.error || !result.data) {
      throw new Error(result.error ?? "Failed to list scoring requests");
    }
    const d = result.data;
    return {
      content: d.content,
      totalElements: d.totalElements,
      totalPages: d.totalPages,
      size: d.size,
      number: d.page,
      first: d.page === 0,
      last: d.last,
    };
  },
};
