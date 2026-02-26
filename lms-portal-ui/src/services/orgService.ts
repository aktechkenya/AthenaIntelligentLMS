import { apiGet, apiPut } from "@/lib/api";

export interface OrgSettings {
  tenantId: string;
  currency: string;
  orgName: string | null;
  countryCode: string | null;
  timezone: string;
}

export interface UpdateOrgSettings {
  currency?: string;
  orgName?: string;
  countryCode?: string;
  timezone?: string;
}

// Org settings live in account-service, proxied via /proxy/auth/
const BASE = "/proxy/auth/api/v1/organization";

export const orgService = {
  async getSettings(): Promise<OrgSettings> {
    const result = await apiGet<OrgSettings>(`${BASE}/settings`);
    if (result.error || !result.data) {
      throw new Error(result.error ?? "Failed to load org settings");
    }
    return result.data;
  },

  async updateSettings(req: UpdateOrgSettings): Promise<OrgSettings> {
    const result = await apiPut<OrgSettings>(`${BASE}/settings`, req);
    if (result.error || !result.data) {
      throw new Error(result.error ?? "Failed to save org settings");
    }
    return result.data;
  },
};
