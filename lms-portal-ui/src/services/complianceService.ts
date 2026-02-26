import { apiGet, type PageResponse } from "@/lib/api";

export interface ComplianceAlert {
  id: string;
  entityId?: string;
  entityType?: string;
  alertType?: string;
  checkType?: string;
  status: string;
  result?: string;
  riskLevel?: string;
  findings?: string;
  checkedBy?: string;
  checkedAt?: string;
  createdAt?: string;
}

/** @deprecated use ComplianceAlert */
export type ComplianceCheck = ComplianceAlert;

const BASE = "/proxy/compliance/api/v1/compliance";

export const complianceService = {
  async listAlerts(page = 0, size = 20): Promise<PageResponse<ComplianceAlert>> {
    const params = new URLSearchParams({ page: String(page), size: String(size) });
    const result = await apiGet<PageResponse<ComplianceAlert>>(`${BASE}/alerts?${params}`);
    if (result.error || !result.data) {
      throw new Error(result.error ?? "Failed to list compliance alerts");
    }
    return result.data;
  },

  /** Backward-compat alias */
  async listChecks(page = 0, size = 20): Promise<PageResponse<ComplianceAlert>> {
    return this.listAlerts(page, size);
  },
};
