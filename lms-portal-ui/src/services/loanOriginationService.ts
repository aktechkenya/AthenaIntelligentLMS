import { apiGet, apiPost, type PageResponse } from "@/lib/api";

export interface LoanApplication {
  id: string;
  customerId: string;
  productId: string;
  requestedAmount: number;
  approvedAmount?: number;
  status: string;
  currency: string;
  tenorMonths: number;
  purpose?: string;
  disbursementAccount?: string;
  reviewNotes?: string;
  creditScore?: number;
  riskGrade?: string;
  interestRate?: number;
  disbursedAmount?: number;
  createdAt: string;
  updatedAt?: string;
}

export interface CreateApplicationRequest {
  customerId: string;
  productId: string;
  requestedAmount: number;
  tenorMonths: number;
  purpose?: string;
  currency: string;
  disbursementAccount?: string;
}

export interface ApproveApplicationRequest {
  approvedAmount: number;
  interestRate: number;
  reviewNotes?: string;
  creditScore?: number;
  riskGrade?: string;
}

export interface RejectApplicationRequest {
  reviewNotes?: string;
  reason?: string;
}

export interface DisburseApplicationRequest {
  disbursedAmount: number;
  disbursementAccount: string;
}

export interface CollateralRequest {
  type: string;
  description: string;
  estimatedValue: number;
  currency?: string;
}

export interface CollateralResponse {
  id: string;
  applicationId: string;
  type: string;
  description: string;
  estimatedValue: number;
  currency: string;
  createdAt: string;
}

export interface NoteRequest {
  content: string;
  noteType?: string;
}

export interface NoteResponse {
  id: string;
  applicationId: string;
  content: string;
  noteType: string;
  createdBy?: string;
  createdAt: string;
}

const BASE = "/proxy/loan-applications/api/v1/loan-applications";

export const loanOriginationService = {
  async listApplications(
    page = 0,
    size = 20,
    status?: string
  ): Promise<PageResponse<LoanApplication>> {
    const params = new URLSearchParams({ page: String(page), size: String(size) });
    if (status) params.set("status", status);
    const result = await apiGet<PageResponse<LoanApplication>>(`${BASE}?${params}`);
    if (result.error || !result.data) {
      throw new Error(result.error ?? "Failed to list applications");
    }
    return result.data;
  },

  async getApplication(id: string): Promise<LoanApplication> {
    const result = await apiGet<LoanApplication>(`${BASE}/${id}`);
    if (result.error || !result.data) {
      throw new Error(result.error ?? "Failed to fetch application");
    }
    return result.data;
  },

  async createApplication(req: CreateApplicationRequest): Promise<LoanApplication> {
    const result = await apiPost<LoanApplication>(BASE, req);
    if (result.error || !result.data) {
      throw new Error(result.error ?? "Failed to create application");
    }
    return result.data;
  },

  async submitApplication(id: string): Promise<LoanApplication> {
    const result = await apiPost<LoanApplication>(`${BASE}/${id}/submit`);
    if (result.error || !result.data) {
      throw new Error(result.error ?? "Failed to submit application");
    }
    return result.data;
  },

  async startReview(id: string): Promise<LoanApplication> {
    const result = await apiPost<LoanApplication>(`${BASE}/${id}/review/start`);
    if (result.error || !result.data) {
      throw new Error(result.error ?? "Failed to start review");
    }
    return result.data;
  },

  async approveApplication(
    id: string,
    req: ApproveApplicationRequest
  ): Promise<LoanApplication> {
    const result = await apiPost<LoanApplication>(`${BASE}/${id}/review/approve`, req);
    if (result.error || !result.data) {
      throw new Error(result.error ?? "Failed to approve application");
    }
    return result.data;
  },

  async rejectApplication(
    id: string,
    req: RejectApplicationRequest
  ): Promise<LoanApplication> {
    const result = await apiPost<LoanApplication>(`${BASE}/${id}/review/reject`, req);
    if (result.error || !result.data) {
      throw new Error(result.error ?? "Failed to reject application");
    }
    return result.data;
  },

  async disburseApplication(
    id: string,
    req: DisburseApplicationRequest
  ): Promise<LoanApplication> {
    const result = await apiPost<LoanApplication>(`${BASE}/${id}/disburse`, req);
    if (result.error || !result.data) {
      throw new Error(result.error ?? "Failed to disburse application");
    }
    return result.data;
  },

  async cancelApplication(id: string, reason?: string): Promise<LoanApplication> {
    const result = await apiPost<LoanApplication>(`${BASE}/${id}/cancel`, { reason });
    if (result.error || !result.data) {
      throw new Error(result.error ?? "Failed to cancel application");
    }
    return result.data;
  },

  async addCollateral(id: string, req: CollateralRequest): Promise<CollateralResponse> {
    const result = await apiPost<CollateralResponse>(`${BASE}/${id}/collateral`, req);
    if (result.error || !result.data) {
      throw new Error(result.error ?? "Failed to add collateral");
    }
    return result.data;
  },

  async addNote(id: string, req: NoteRequest): Promise<NoteResponse> {
    const result = await apiPost<NoteResponse>(`${BASE}/${id}/notes`, req);
    if (result.error || !result.data) {
      throw new Error(result.error ?? "Failed to add note");
    }
    return result.data;
  },
};
