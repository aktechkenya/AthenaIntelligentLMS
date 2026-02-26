import { apiGet, type PageResponse } from "@/lib/api";

export interface JournalEntryLine {
  id: string;
  accountId: string;
  accountCode?: string;
  accountName?: string;
  lineNo?: number;
  description?: string;
  debitAmount: number;
  creditAmount: number;
  currency?: string;
}

export interface JournalEntry {
  id: string;
  tenantId?: string;
  reference: string;
  description: string;
  entryDate: string;
  status: string;
  sourceEvent?: string;
  sourceId?: string;
  totalDebit: number;
  totalCredit: number;
  postedBy?: string;
  createdAt?: string;
  lines?: JournalEntryLine[];
}

export interface TrialBalanceAccount {
  accountId: string;
  accountCode: string;
  accountName: string;
  accountType: string;
  balanceType: string;
  balance: number;
  currency?: string;
  periodYear?: number;
  periodMonth?: number;
}

export interface TrialBalanceResponse {
  periodYear: number;
  periodMonth: number;
  accounts: TrialBalanceAccount[];
  totalDebits: number;
  totalCredits: number;
  balanced: boolean;
}

export interface GLAccount {
  id: string;
  accountCode: string;
  accountName: string;
  accountType: string;
  parentId?: string;
  currency?: string;
  status?: string;
  balance?: number;
  createdAt?: string;
}

const BASE = "/proxy/accounting/api/v1/accounting";

export const accountingService = {
  async listGLAccounts(page = 0, size = 50): Promise<PageResponse<GLAccount>> {
    const params = new URLSearchParams({ page: String(page), size: String(size) });
    const result = await apiGet<PageResponse<GLAccount>>(`${BASE}/accounts?${params}`);
    if (result.error || !result.data) {
      throw new Error(result.error ?? "Failed to list GL accounts");
    }
    return result.data;
  },

  async listJournalEntries(page = 0, size = 20): Promise<PageResponse<JournalEntry>> {
    const params = new URLSearchParams({ page: String(page), size: String(size) });
    const result = await apiGet<PageResponse<JournalEntry>>(`${BASE}/journal-entries?${params}`);
    if (result.error || !result.data) {
      throw new Error(result.error ?? "Failed to list journal entries");
    }
    return result.data;
  },

  async getTrialBalance(): Promise<TrialBalanceResponse> {
    const result = await apiGet<TrialBalanceResponse>(`${BASE}/trial-balance`);
    if (result.error || !result.data) {
      throw new Error(result.error ?? "Failed to fetch trial balance");
    }
    return result.data;
  },
};
