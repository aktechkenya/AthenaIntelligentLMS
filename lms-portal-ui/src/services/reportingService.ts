import { apiGet } from "@/lib/api";

export interface ReportingSummary {
  tenantId?: string;
  asOfDate?: string;
  // API-native fields
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
  // UI convenience aliases (mapped from API fields)
  activeLoanCount?: number;
  disbursedThisMonth?: number;
  parRatio?: number;
  nplRatio?: number;
  collectionRate?: number;
  period?: string;
  currency?: string;
}

const BASE = "/proxy/reporting/api/v1/reporting";

export const reportingService = {
  async getSummary(period?: string): Promise<ReportingSummary> {
    const params = period ? `?period=${period}` : "?period=CURRENT_MONTH";
    const result = await apiGet<ReportingSummary>(`${BASE}/summary${params}`);
    if (result.error || !result.data) {
      throw new Error(result.error ?? "Failed to fetch reporting summary");
    }
    // Map API fields to UI convenience aliases
    const d = result.data;
    return {
      ...d,
      activeLoanCount: d.activeLoans,
      disbursedThisMonth: d.totalDisbursed,
      totalOutstanding: d.totalOutstanding,
      parRatio: d.par30 && d.totalLoans ? (d.par30 / d.totalLoans) * 100 : 0,
    };
  },
};
