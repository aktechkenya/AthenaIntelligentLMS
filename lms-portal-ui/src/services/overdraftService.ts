import { apiGet, apiPost, type PageResponse } from "@/lib/api";

export interface CustomerWallet {
  id: string;
  tenantId: string;
  customerId: string;
  accountNumber: string;
  currency: string;
  currentBalance: number;
  availableBalance: number;
  status: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface OverdraftFacility {
  id: string;
  tenantId: string;
  walletId: string;
  customerId: string;
  creditScore: number;
  creditBand: string;
  approvedLimit: number;
  drawnAmount: number;
  availableOverdraft: number;
  interestRate: number;
  status: string;
  appliedAt?: string;
  approvedAt?: string;
  createdAt?: string;
}

export interface WalletTransaction {
  id: string;
  walletId: string;
  transactionType: string;
  amount: number;
  balanceBefore: number;
  balanceAfter: number;
  reference?: string;
  description?: string;
  createdAt?: string;
}

export interface InterestCharge {
  id: string;
  facilityId: string;
  chargeDate: string;
  drawnAmount: number;
  dailyRate: number;
  interestCharged: number;
  reference: string;
  createdAt?: string;
}

export interface OverdraftSummary {
  totalFacilities: number;
  activeFacilities: number;
  totalApprovedLimit: number;
  totalDrawnAmount: number;
  totalAvailableOverdraft: number;
  facilitiesByBand: Record<string, number>;
  drawnByBand: Record<string, number>;
}

const BASE = "/proxy/overdraft/api/v1";

export const overdraftService = {
  async listWallets(): Promise<CustomerWallet[]> {
    const result = await apiGet<CustomerWallet[]>(`${BASE}/wallets`);
    if (result.error || !result.data) throw new Error(result.error ?? "Failed to list wallets");
    return result.data;
  },

  async createWallet(customerId: string, currency = "KES"): Promise<CustomerWallet> {
    const result = await apiPost<CustomerWallet>(`${BASE}/wallets`, { customerId, currency });
    if (result.error || !result.data) throw new Error(result.error ?? "Failed to create wallet");
    return result.data;
  },

  async getWallet(walletId: string): Promise<CustomerWallet> {
    const result = await apiGet<CustomerWallet>(`${BASE}/wallets/${walletId}`);
    if (result.error || !result.data) throw new Error(result.error ?? "Failed to get wallet");
    return result.data;
  },

  async deposit(walletId: string, amount: number, reference: string): Promise<WalletTransaction> {
    const result = await apiPost<WalletTransaction>(`${BASE}/wallets/${walletId}/deposit`, { amount, reference });
    if (result.error || !result.data) throw new Error(result.error ?? "Failed to deposit");
    return result.data;
  },

  async withdraw(walletId: string, amount: number, reference: string): Promise<WalletTransaction> {
    const result = await apiPost<WalletTransaction>(`${BASE}/wallets/${walletId}/withdraw`, { amount, reference });
    if (result.error || !result.data) throw new Error(result.error ?? "Failed to withdraw");
    return result.data;
  },

  async getTransactions(walletId: string, page = 0, size = 20): Promise<PageResponse<WalletTransaction>> {
    const params = new URLSearchParams({ page: String(page), size: String(size) });
    const result = await apiGet<PageResponse<WalletTransaction>>(`${BASE}/wallets/${walletId}/transactions?${params}`);
    if (result.error || !result.data) throw new Error(result.error ?? "Failed to get transactions");
    return result.data;
  },

  async applyOverdraft(walletId: string): Promise<OverdraftFacility> {
    const result = await apiPost<OverdraftFacility>(`${BASE}/wallets/${walletId}/overdraft/apply`, {});
    if (result.error || !result.data) throw new Error(result.error ?? "Failed to apply overdraft");
    return result.data;
  },

  async getFacility(walletId: string): Promise<OverdraftFacility> {
    const result = await apiGet<OverdraftFacility>(`${BASE}/wallets/${walletId}/overdraft`);
    if (result.error || !result.data) throw new Error(result.error ?? "Failed to get facility");
    return result.data;
  },

  async suspendFacility(walletId: string): Promise<OverdraftFacility> {
    const result = await apiPost<OverdraftFacility>(`${BASE}/wallets/${walletId}/overdraft/suspend`, {});
    if (result.error || !result.data) throw new Error(result.error ?? "Failed to suspend facility");
    return result.data;
  },

  async getCharges(walletId: string): Promise<InterestCharge[]> {
    const result = await apiGet<InterestCharge[]>(`${BASE}/wallets/${walletId}/overdraft/charges`);
    if (result.error || !result.data) throw new Error(result.error ?? "Failed to get charges");
    return result.data;
  },

  async getSummary(): Promise<OverdraftSummary> {
    const result = await apiGet<OverdraftSummary>(`${BASE}/overdraft/summary`);
    if (result.error || !result.data) throw new Error(result.error ?? "Failed to get summary");
    return result.data;
  },
};
