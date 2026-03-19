import { apiGet, apiPost, apiPut, type PageResponse } from "@/lib/api";

export interface DepositProduct {
  id: string;
  tenantId: string;
  productCode: string;
  name: string;
  description?: string;
  productCategory: string;
  status: string;
  currency: string;
  interestRate: number;
  interestCalcMethod: string;
  interestPostingFreq: string;
  interestCompoundFreq: string;
  accrualFrequency: string;
  minOpeningBalance: number;
  minOperatingBalance: number;
  minBalanceForInterest: number;
  minTermDays?: number;
  maxTermDays?: number;
  earlyWithdrawalPenaltyRate?: number;
  autoRenew: boolean;
  dormancyDaysThreshold: number;
  dormancyChargeAmount?: number;
  monthlyMaintenanceFee?: number;
  maxWithdrawalsPerMonth?: number;
  interestTiers: DepositInterestTier[];
  version: number;
  createdBy?: string;
  createdAt: string;
  updatedAt: string;
}

export interface DepositInterestTier {
  id?: string;
  fromAmount: number;
  toAmount: number;
  rate: number;
}

export interface CreateDepositProductRequest {
  productCode: string;
  name: string;
  description?: string;
  productCategory: string;
  currency?: string;
  interestRate?: number;
  interestCalcMethod?: string;
  interestPostingFreq?: string;
  interestCompoundFreq?: string;
  accrualFrequency?: string;
  minOpeningBalance?: number;
  minOperatingBalance?: number;
  minBalanceForInterest?: number;
  minTermDays?: number;
  maxTermDays?: number;
  earlyWithdrawalPenaltyRate?: number;
  autoRenew?: boolean;
  dormancyDaysThreshold?: number;
  dormancyChargeAmount?: number;
  monthlyMaintenanceFee?: number;
  maxWithdrawalsPerMonth?: number;
  interestTiers?: DepositInterestTier[];
}

const BASE = "/proxy/products/api/v1/deposit-products";

export const depositProductService = {
  async listProducts(page = 0, size = 20): Promise<PageResponse<DepositProduct>> {
    const params = new URLSearchParams({ page: String(page), size: String(size) });
    const result = await apiGet<PageResponse<DepositProduct>>(`${BASE}?${params}`);
    if (result.error || !result.data) {
      throw new Error(result.error ?? "Failed to list deposit products");
    }
    return result.data;
  },

  async getProduct(id: string): Promise<DepositProduct> {
    const result = await apiGet<DepositProduct>(`${BASE}/${id}`);
    if (result.error || !result.data) {
      throw new Error(result.error ?? "Failed to fetch deposit product");
    }
    return result.data;
  },

  async createProduct(req: CreateDepositProductRequest): Promise<DepositProduct> {
    const result = await apiPost<DepositProduct>(BASE, req);
    if (result.error || !result.data) {
      throw new Error(result.error ?? "Failed to create deposit product");
    }
    return result.data;
  },

  async updateProduct(id: string, req: CreateDepositProductRequest): Promise<DepositProduct> {
    const result = await apiPut<DepositProduct>(`${BASE}/${id}`, req);
    if (result.error || !result.data) {
      throw new Error(result.error ?? "Failed to update deposit product");
    }
    return result.data;
  },

  async activateProduct(id: string): Promise<DepositProduct> {
    const result = await apiPost<DepositProduct>(`${BASE}/${id}/activate`, {});
    if (result.error || !result.data) {
      throw new Error(result.error ?? "Failed to activate deposit product");
    }
    return result.data;
  },

  async deactivateProduct(id: string): Promise<DepositProduct> {
    const result = await apiPost<DepositProduct>(`${BASE}/${id}/deactivate`, {});
    if (result.error || !result.data) {
      throw new Error(result.error ?? "Failed to deactivate deposit product");
    }
    return result.data;
  },
};
