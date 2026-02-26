import { apiGet, apiPost, type PageResponse } from "@/lib/api";

export interface Account {
  id: string;
  accountNumber: string;
  accountType: string;
  customerId: string;
  status: string;
  currency: string;
  balance?: number;
  branchCode?: string;
  createdAt?: string;
}

export interface CreateAccountRequest {
  customerId: string;
  accountType: string;
  currency: string;
  branchCode?: string;
}

export interface BalanceResponse {
  accountId: string;
  accountNumber: string;
  currency: string;
  balance: number;
  availableBalance: number;
  asOf: string;
}

export interface Transaction {
  id: string;
  accountId: string;
  transactionType: string;
  amount: number;
  currency: string;
  description?: string;
  reference?: string;
  valueDate: string;
  createdAt?: string;
  runningBalance?: number;
}

export interface TransferRequest {
  sourceAccountId: string;
  destinationAccountId?: string;
  destinationAccountNumber?: string;
  amount: number;
  transferType: string;
  narration?: string;
  idempotencyKey?: string;
}

export interface TransferResponse {
  id: string;
  sourceAccountId: string;
  sourceAccountNumber?: string;
  destinationAccountId: string;
  destinationAccountNumber?: string;
  amount: number;
  currency: string;
  transferType: string;
  status: string;
  reference: string;
  narration?: string;
  chargeAmount: number;
  initiatedAt: string;
  completedAt?: string;
  failedReason?: string;
}

export interface StatementResponse {
  accountNumber: string;
  customerName: string;
  currency: string;
  openingBalance: number;
  closingBalance: number;
  periodFrom: string;
  periodTo: string;
  transactions: PageResponse<Transaction>;
}

const BASE = "/proxy/auth/api/v1/accounts";
const TRANSFER_BASE = "/proxy/auth/api/v1/transfers";

export const accountService = {
  async listAccounts(
    page = 0,
    size = 20
  ): Promise<PageResponse<Account>> {
    const params = new URLSearchParams({ page: String(page), size: String(size) });
    const result = await apiGet<PageResponse<Account>>(`${BASE}?${params}`);
    if (result.error || !result.data) {
      throw new Error(result.error ?? "Failed to list accounts");
    }
    return result.data;
  },

  async getAccount(id: string): Promise<Account> {
    const result = await apiGet<Account>(`${BASE}/${id}`);
    if (result.error || !result.data) {
      throw new Error(result.error ?? "Failed to fetch account");
    }
    return result.data;
  },

  async createAccount(req: CreateAccountRequest): Promise<Account> {
    const result = await apiPost<Account>(BASE, req);
    if (result.error || !result.data) {
      throw new Error(result.error ?? "Failed to create account");
    }
    return result.data;
  },

  async getBalance(id: string): Promise<BalanceResponse> {
    const result = await apiGet<BalanceResponse>(`${BASE}/${id}/balance`);
    if (result.error || !result.data) {
      throw new Error(result.error ?? "Failed to fetch balance");
    }
    return result.data;
  },

  async getTransactions(
    id: string,
    page = 0,
    size = 20
  ): Promise<PageResponse<Transaction>> {
    const params = new URLSearchParams({ page: String(page), size: String(size) });
    const result = await apiGet<PageResponse<Transaction>>(
      `${BASE}/${id}/transactions?${params}`
    );
    if (result.error || !result.data) {
      throw new Error(result.error ?? "Failed to fetch transactions");
    }
    return result.data;
  },

  async getCustomerAccounts(customerId: string): Promise<Account[]> {
    const result = await apiGet<Account[]>(`${BASE}/customer/${encodeURIComponent(customerId)}`);
    if (result.error || !result.data) {
      throw new Error(result.error ?? "Failed to fetch customer accounts");
    }
    return result.data;
  },

  async getStatement(
    accountId: string,
    from: string,
    to: string,
    page = 0,
    size = 50
  ): Promise<StatementResponse> {
    const params = new URLSearchParams({
      from, to, page: String(page), size: String(size),
    });
    const result = await apiGet<StatementResponse>(`${BASE}/${accountId}/statement?${params}`);
    if (result.error || !result.data) {
      throw new Error(result.error ?? "Failed to fetch statement");
    }
    return result.data;
  },

  async initiateTransfer(req: TransferRequest): Promise<TransferResponse> {
    const result = await apiPost<TransferResponse>(TRANSFER_BASE, req);
    if (result.error || !result.data) {
      throw new Error(result.error ?? "Failed to initiate transfer");
    }
    return result.data;
  },

  async getTransfer(id: string): Promise<TransferResponse> {
    const result = await apiGet<TransferResponse>(`${TRANSFER_BASE}/${id}`);
    if (result.error || !result.data) {
      throw new Error(result.error ?? "Failed to fetch transfer");
    }
    return result.data;
  },

  async getTransfersByAccount(
    accountId: string,
    page = 0,
    size = 20
  ): Promise<PageResponse<TransferResponse>> {
    const params = new URLSearchParams({ page: String(page), size: String(size) });
    const result = await apiGet<PageResponse<TransferResponse>>(
      `${TRANSFER_BASE}/account/${accountId}?${params}`
    );
    if (result.error || !result.data) {
      throw new Error(result.error ?? "Failed to fetch transfers");
    }
    return result.data;
  },
};
