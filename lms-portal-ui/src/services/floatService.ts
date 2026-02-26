import { apiGet, apiPost, type PageResponse } from "@/lib/api";

export interface FloatAccount {
  id: string;
  accountName: string;
  accountCode: string;
  currency: string;
  floatLimit: number;
  drawnAmount: number;
  available: number;
  status: string;
  description?: string;
  createdAt?: string;
}

export interface CreateFloatAccountRequest {
  accountName: string;
  accountCode: string;
  currency: string;
  floatLimit: number;
  description?: string;
}

export interface FloatTransaction {
  id: string;
  floatAccountId: string;
  transactionType: string;
  amount: number;
  currency: string;
  description?: string;
  reference?: string;
  valueDate: string;
  createdAt?: string;
}

const BASE = "/proxy/float/api/v1/float";

export const floatService = {
  async listFloatAccounts(): Promise<FloatAccount[]> {
    const result = await apiGet<FloatAccount[]>(`${BASE}/accounts`);
    if (result.error || !result.data) {
      throw new Error(result.error ?? "Failed to list float accounts");
    }
    return result.data;
  },

  async createFloatAccount(req: CreateFloatAccountRequest): Promise<FloatAccount> {
    const result = await apiPost<FloatAccount>(`${BASE}/accounts`, req);
    if (result.error || !result.data) {
      throw new Error(result.error ?? "Failed to create float account");
    }
    return result.data;
  },

  async getTransactions(
    id: string,
    page = 0,
    size = 20
  ): Promise<PageResponse<FloatTransaction>> {
    const params = new URLSearchParams({ page: String(page), size: String(size) });
    const result = await apiGet<PageResponse<FloatTransaction>>(
      `${BASE}/accounts/${id}/transactions?${params}`
    );
    if (result.error || !result.data) {
      throw new Error(result.error ?? "Failed to fetch float transactions");
    }
    return result.data;
  },
};
