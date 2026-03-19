import { apiGet, apiPost, apiPut, type PageResponse } from "@/lib/api";

// --- Interfaces ---
export interface CollectionCase {
  id: string;
  tenantId: string;
  loanId: string;
  customerId?: string;
  caseNumber: string;
  status: string;
  priority: string;
  currentDpd: number;
  currentStage: string;
  outstandingAmount: number;
  assignedTo?: string;
  openedAt: string;
  closedAt?: string;
  lastActionAt?: string;
  notes?: string;
  createdAt: string;
  updatedAt: string;
}

export interface CollectionAction {
  id: string;
  caseId: string;
  actionType: string;
  outcome?: string;
  notes?: string;
  contactPerson?: string;
  contactMethod?: string;
  performedBy?: string;
  performedAt: string;
  nextActionDate?: string;
  createdAt: string;
}

export interface PromiseToPay {
  id: string;
  caseId: string;
  promisedAmount: number;
  promiseDate: string;
  status: string;
  notes?: string;
  createdBy?: string;
  fulfilledAt?: string;
  brokenAt?: string;
  createdAt: string;
  updatedAt: string;
}

export interface CollectionSummary {
  totalOpenCases: number;
  watchCases: number;
  substandardCases: number;
  doubtfulCases: number;
  lossCases: number;
  criticalPriorityCases: number;
  totalOutstandingAmount: number;
  watchAmount: number;
  substandardAmount: number;
  doubtfulAmount: number;
  lossAmount: number;
  pendingPtpCount: number;
  overdueFollowUpCount: number;
  tenantId: string;
}

export interface CaseDetail {
  case: CollectionCase;
  actions: CollectionAction[];
  ptps: PromiseToPay[];
}

export interface AddActionRequest {
  actionType: string;
  outcome?: string;
  notes?: string;
  contactPerson?: string;
  contactMethod?: string;
  performedBy?: string;
  nextActionDate?: string;
}

export interface AddPtpRequest {
  promisedAmount: number;
  promiseDate: string;
  notes?: string;
  createdBy?: string;
}

export interface UpdateCaseRequest {
  assignedTo?: string;
  priority?: string;
  notes?: string;
}

const BASE = "/proxy/collections/api/v1/collections";

export const collectionsService = {
  // Summary
  async getSummary(): Promise<CollectionSummary> {
    const r = await apiGet<CollectionSummary>(`${BASE}/summary`);
    if (r.error || !r.data) throw new Error(r.error ?? "Failed to get summary");
    return r.data;
  },

  // Cases
  async listCases(page = 0, size = 20, params?: Record<string, string>): Promise<PageResponse<CollectionCase>> {
    const q = new URLSearchParams({ page: String(page), size: String(size), ...params });
    const r = await apiGet<PageResponse<CollectionCase>>(`${BASE}/cases?${q}`);
    if (r.error || !r.data) throw new Error(r.error ?? "Failed to list cases");
    return r.data;
  },

  async getCase(id: string): Promise<CollectionCase> {
    const r = await apiGet<CollectionCase>(`${BASE}/cases/${id}`);
    if (r.error || !r.data) throw new Error(r.error ?? "Failed to get case");
    return r.data;
  },

  async getCaseDetail(id: string): Promise<CaseDetail> {
    const r = await apiGet<CaseDetail>(`${BASE}/cases/${id}/detail`);
    if (r.error || !r.data) throw new Error(r.error ?? "Failed to get case detail");
    return r.data;
  },

  async getCaseByLoan(loanId: string): Promise<CollectionCase> {
    const r = await apiGet<CollectionCase>(`${BASE}/cases/loan/${loanId}`);
    if (r.error || !r.data) throw new Error(r.error ?? "Failed to get case by loan");
    return r.data;
  },

  async updateCase(id: string, req: UpdateCaseRequest): Promise<CollectionCase> {
    const r = await apiPut<CollectionCase>(`${BASE}/cases/${id}`, req);
    if (r.error || !r.data) throw new Error(r.error ?? "Failed to update case");
    return r.data;
  },

  async closeCase(id: string): Promise<CollectionCase> {
    const r = await apiPost<CollectionCase>(`${BASE}/cases/${id}/close`);
    if (r.error || !r.data) throw new Error(r.error ?? "Failed to close case");
    return r.data;
  },

  // Actions
  async addAction(caseId: string, req: AddActionRequest): Promise<CollectionAction> {
    const r = await apiPost<CollectionAction>(`${BASE}/cases/${caseId}/actions`, req);
    if (r.error || !r.data) throw new Error(r.error ?? "Failed to add action");
    return r.data;
  },

  async listActions(caseId: string): Promise<CollectionAction[]> {
    const r = await apiGet<CollectionAction[]>(`${BASE}/cases/${caseId}/actions`);
    if (r.error || !r.data) throw new Error(r.error ?? "Failed to list actions");
    return r.data;
  },

  // PTPs
  async addPtp(caseId: string, req: AddPtpRequest): Promise<PromiseToPay> {
    const r = await apiPost<PromiseToPay>(`${BASE}/cases/${caseId}/ptps`, req);
    if (r.error || !r.data) throw new Error(r.error ?? "Failed to add PTP");
    return r.data;
  },

  async listPtps(caseId: string): Promise<PromiseToPay[]> {
    const r = await apiGet<PromiseToPay[]>(`${BASE}/cases/${caseId}/ptps`);
    if (r.error || !r.data) throw new Error(r.error ?? "Failed to list PTPs");
    return r.data;
  },
};
