import { apiGet, apiPost, type PageResponse } from "@/lib/api";

export interface ReportingSummary {
  tenantId?: string;
  asOfDate?: string;
  totalLoans?: number;
  activeLoans?: number;
  closedLoans?: number;
  defaultedLoans?: number;
  totalDisbursed?: number;
  totalOutstanding?: number;
  totalCollected?: number;
  par30?: number;
  par90?: number;
  watchLoans?: number;
  substandardLoans?: number;
  doubtfulLoans?: number;
  lossLoans?: number;
  activeLoanCount?: number;
  disbursedThisMonth?: number;
  parRatio?: number;
  nplRatio?: number;
  collectionRate?: number;
  period?: string;
  currency?: string;
}

export interface ReportEvent {
  id: string;
  eventType?: string;
  eventCategory?: string;
  sourceService?: string;
  payload?: string;
  occurredAt?: string;
  createdAt?: string;
}

export interface PortfolioSnapshot {
  id: string;
  tenantId?: string;
  snapshotDate?: string;
  totalLoans?: number;
  activeLoans?: number;
  totalDisbursed?: number;
  totalOutstanding?: number;
  par30?: number;
  createdAt?: string;
}

const BASE = "/proxy/reporting/api/v1/reporting";

export const reportingService = {
  async getSummary(period?: string): Promise<ReportingSummary> {
    const params = period ? `?period=${period}` : "?period=CURRENT_MONTH";
    const result = await apiGet<ReportingSummary>(`${BASE}/summary${params}`);
    if (result.error || !result.data) {
      throw new Error(result.error ?? "Failed to fetch reporting summary");
    }
    const d = result.data;
    return {
      ...d,
      activeLoanCount: d.activeLoans,
      disbursedThisMonth: d.totalDisbursed,
      totalOutstanding: d.totalOutstanding,
      parRatio: d.par30 && d.totalLoans ? (d.par30 / d.totalLoans) * 100 : 0,
    };
  },

  async getEvents(page = 0, size = 20): Promise<PageResponse<ReportEvent>> {
    const params = new URLSearchParams({ page: String(page), size: String(size) });
    const result = await apiGet<PageResponse<ReportEvent>>(`${BASE}/events?${params}`);
    if (result.error || !result.data) {
      throw new Error(result.error ?? "Failed to fetch report events");
    }
    return result.data;
  },

  async getSnapshots(page = 0, size = 20): Promise<PageResponse<PortfolioSnapshot>> {
    const params = new URLSearchParams({ page: String(page), size: String(size) });
    const result = await apiGet<PageResponse<PortfolioSnapshot>>(`${BASE}/snapshots?${params}`);
    if (result.error || !result.data) {
      throw new Error(result.error ?? "Failed to fetch snapshots");
    }
    return result.data;
  },

  async generateSnapshot(): Promise<PortfolioSnapshot> {
    const result = await apiPost<PortfolioSnapshot>(`${BASE}/snapshots/generate`);
    if (result.error || !result.data) {
      throw new Error(result.error ?? "Failed to generate snapshot");
    }
    return result.data;
  },
};
