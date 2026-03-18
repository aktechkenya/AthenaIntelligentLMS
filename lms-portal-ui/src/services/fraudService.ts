import { apiGet, apiPost, apiPut, apiDelete, type PageResponse } from "@/lib/api";

// ─── Types ───────────────────────────────────────────────────────────────────

export type AlertSeverity = "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";
export type AlertStatus = "OPEN" | "UNDER_REVIEW" | "ESCALATED" | "CONFIRMED_FRAUD" | "FALSE_POSITIVE" | "CLOSED";
export type AlertSource = "RULE_ENGINE" | "ML_MODEL" | "MANUAL" | "WATCHLIST";
export type RiskLevel = "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";

export interface FraudAlert {
  id: string;
  tenantId: string;
  alertType: string;
  severity: AlertSeverity;
  status: AlertStatus;
  source: AlertSource;
  ruleCode?: string;
  customerId?: string;
  subjectType: string;
  subjectId: string;
  description: string;
  triggerEvent?: string;
  triggerAmount?: number;
  riskScore?: number;
  escalated: boolean;
  escalatedToCompliance: boolean;
  assignedTo?: string;
  resolvedBy?: string;
  resolvedAt?: string;
  resolution?: string;
  resolutionNotes?: string;
  explanation?: Record<string, unknown>;
  createdAt: string;
  updatedAt: string;
}

export interface CustomerRiskProfile {
  customerId: string;
  tenantId: string;
  riskScore: number;
  riskLevel: RiskLevel;
  totalAlerts: number;
  openAlerts: number;
  confirmedFraud: number;
  falsePositives: number;
  lastAlertAt?: string;
  factors?: Record<string, unknown>;
}

export interface FraudSummary {
  tenantId: string;
  openAlerts: number;
  underReviewAlerts: number;
  escalatedAlerts: number;
  confirmedFraud: number;
  criticalAlerts: number;
  highRiskCustomers: number;
  criticalRiskCustomers: number;
}

export interface ResolveAlertRequest {
  resolvedBy: string;
  confirmedFraud?: boolean;
  notes?: string;
}

export interface AssignAlertRequest {
  assignee: string;
}

export type CaseStatus = "OPEN" | "INVESTIGATING" | "PENDING_REVIEW" | "ESCALATED" | "CLOSED_CONFIRMED" | "CLOSED_FALSE_POSITIVE" | "CLOSED_INCONCLUSIVE";

export interface FraudCase {
  id: string;
  tenantId: string;
  caseNumber: string;
  title: string;
  description?: string;
  status: CaseStatus;
  priority: AlertSeverity;
  customerId?: string;
  assignedTo?: string;
  totalExposure?: number;
  confirmedLoss?: number;
  alertIds?: string[];
  tags?: string[];
  closedBy?: string;
  outcome?: string;
  closedAt?: string;
  createdAt: string;
  updatedAt: string;
  notes?: CaseNote[];
}

export interface CaseNote {
  id: string;
  author: string;
  content: string;
  noteType: string;
  createdAt: string;
}

export interface FraudRule {
  id: string;
  ruleCode: string;
  ruleName: string;
  description?: string;
  category: string;
  severity: AlertSeverity;
  eventTypes: string;
  enabled: boolean;
  parameters?: Record<string, unknown>;
  createdAt: string;
  updatedAt: string;
}

export interface NetworkNode {
  customerId: string;
  riskLevel: string;
  linkCount: number;
  links: NetworkLink[];
}

export interface NetworkLink {
  linkedCustomerId: string;
  linkType: string;
  linkValue: string;
  strength: number;
  flagged: boolean;
}

export interface AuditLogEntry {
  id: string;
  action: string;
  entityType: string;
  entityId: string;
  performedBy: string;
  description?: string;
  changes?: Record<string, unknown>;
  createdAt: string;
}

export type SarStatus = "DRAFT" | "PENDING_REVIEW" | "APPROVED" | "FILED" | "REJECTED";
export type SarReportType = "SAR" | "CTR";

export interface SarReport {
  id: string;
  tenantId: string;
  reportNumber: string;
  reportType: SarReportType;
  status: SarStatus;
  subjectCustomerId?: string;
  subjectName?: string;
  subjectNationalId?: string;
  narrative?: string;
  suspiciousAmount?: number;
  activityStartDate?: string;
  activityEndDate?: string;
  alertIds?: string[];
  caseId?: string;
  preparedBy?: string;
  reviewedBy?: string;
  filedBy?: string;
  filedAt?: string;
  filingReference?: string;
  regulator?: string;
  filingDeadline?: string;
  createdAt: string;
  updatedAt: string;
}

export interface WatchlistEntry {
  id: string;
  tenantId: string;
  listType: string;
  entryType: string;
  name?: string;
  nationalId?: string;
  phone?: string;
  reason?: string;
  source?: string;
  active: boolean;
  expiresAt?: string;
  createdAt: string;
  updatedAt: string;
}

export interface FraudAnalytics {
  totalAlerts: number;
  resolvedAlerts: number;
  resolutionRate: number;
  activeCases: number;
  confirmedFraudCount: number;
  falsePositiveCount: number;
  precisionRate: number;
  ruleEffectiveness: { ruleCode: string; totalTriggers: number; confirmedFraud: number; falsePositives: number; precisionRate: number }[];
  dailyTrend: { date: string; count: number }[];
  alertsByType: { type: string; count: number }[];
}

export interface FraudEvent {
  id: string;
  tenantId: string;
  eventType: string;
  sourceService?: string;
  customerId?: string;
  subjectId?: string;
  amount?: number;
  riskScore?: number;
  rulesTriggered?: string;
  createdAt: string;
}

export interface BatchScreeningResult {
  customersScreened: number;
  matchesFound: number;
  alertsCreated: number;
  matchedCustomerIds: string[];
}

export interface CaseTimelineEvent {
  action: string;
  description?: string;
  performedBy: string;
  timestamp: string;
}

export interface CaseTimeline {
  caseId: string;
  caseNumber: string;
  events: CaseTimelineEvent[];
}

export interface MLScoringResult {
  score: number;
  riskLevel: string;
  modelAvailable: boolean;
  details: Record<string, unknown>;
  latencyMs: number;
}

export interface ScoringHistoryEntry {
  id: string;
  tenantId: string;
  customerId: string;
  eventType?: string;
  amount?: number;
  mlScore: number;
  riskLevel: string;
  modelAvailable: boolean;
  latencyMs?: number;
  ruleScore?: number;
  anomalyScore?: number;
  lgbmScore?: number;
  modelDetails?: string;
  createdAt: string;
}

export interface ScoringStats {
  totalScored: number;
  lowCount: number;
  mediumCount: number;
  highCount: number;
  criticalCount: number;
  avgScore: number;
  avgLatencyMs: number;
  dailyVolume: { date: string; count: number }[];
}

export interface MLHealthStatus {
  healthy: boolean;
  models: Record<string, string>;
}

export interface TrainingStatus {
  anomaly: Record<string, unknown>;
  lgbm: Record<string, unknown>;
}

// ─── API Client ──────────────────────────────────────────────────────────────

const BASE = "/proxy/fraud/api/fraud";

export const fraudService = {
  // ─── Summary ─────────────────────────────────────────────────────────────────
  async getSummary(): Promise<FraudSummary> {
    const result = await apiGet<FraudSummary>(`${BASE}/summary`);
    if (result.error || !result.data) throw new Error(result.error ?? "Failed to fetch fraud summary");
    return result.data;
  },

  // ─── Alerts ──────────────────────────────────────────────────────────────────
  async listAlerts(page = 0, size = 20, status?: AlertStatus): Promise<PageResponse<FraudAlert>> {
    const params = new URLSearchParams({ page: String(page), size: String(size), sort: "createdAt,desc" });
    if (status) params.set("status", status);
    const result = await apiGet<PageResponse<FraudAlert>>(`${BASE}/alerts?${params}`);
    if (result.error || !result.data) throw new Error(result.error ?? "Failed to list fraud alerts");
    return result.data;
  },

  async getAlert(id: string): Promise<FraudAlert> {
    const result = await apiGet<FraudAlert>(`${BASE}/alerts/${id}`);
    if (result.error || !result.data) throw new Error(result.error ?? "Failed to get fraud alert");
    return result.data;
  },

  async resolveAlert(id: string, req: ResolveAlertRequest): Promise<FraudAlert> {
    const result = await apiPut<FraudAlert>(`${BASE}/alerts/${id}/resolve`, req);
    if (result.error || !result.data) throw new Error(result.error ?? "Failed to resolve alert");
    return result.data;
  },

  async assignAlert(id: string, req: AssignAlertRequest): Promise<FraudAlert> {
    const result = await apiPut<FraudAlert>(`${BASE}/alerts/${id}/assign`, req);
    if (result.error || !result.data) throw new Error(result.error ?? "Failed to assign alert");
    return result.data;
  },

  // ─── Customer Risk ────────────────────────────────────────────────────────────
  async getCustomerRisk(customerId: string): Promise<CustomerRiskProfile> {
    const result = await apiGet<CustomerRiskProfile>(`${BASE}/customer/${customerId}/risk`);
    if (result.error || !result.data) throw new Error(result.error ?? "Failed to get customer risk");
    return result.data;
  },

  async listCustomerAlerts(customerId: string, page = 0, size = 20): Promise<PageResponse<FraudAlert>> {
    const params = new URLSearchParams({ page: String(page), size: String(size) });
    const result = await apiGet<PageResponse<FraudAlert>>(`${BASE}/customer/${customerId}/alerts?${params}`);
    if (result.error || !result.data) throw new Error(result.error ?? "Failed to list customer alerts");
    return result.data;
  },

  async listHighRiskCustomers(page = 0, size = 20): Promise<PageResponse<CustomerRiskProfile>> {
    const params = new URLSearchParams({ page: String(page), size: String(size) });
    const result = await apiGet<PageResponse<CustomerRiskProfile>>(`${BASE}/high-risk-customers?${params}`);
    if (result.error || !result.data) throw new Error(result.error ?? "Failed to list high risk customers");
    return result.data;
  },

  // ─── Cases ──────────────────────────────────────────────────────────────────
  async listCases(page = 0, size = 20, status?: CaseStatus): Promise<PageResponse<FraudCase>> {
    const params = new URLSearchParams({ page: String(page), size: String(size), sort: "createdAt,desc" });
    if (status) params.set("status", status);
    const result = await apiGet<PageResponse<FraudCase>>(`${BASE}/cases?${params}`);
    if (result.error || !result.data) throw new Error(result.error ?? "Failed to list cases");
    return result.data;
  },

  async getCase(id: string): Promise<FraudCase> {
    const result = await apiGet<FraudCase>(`${BASE}/cases/${id}`);
    if (result.error || !result.data) throw new Error(result.error ?? "Failed to get case");
    return result.data;
  },

  async createCase(req: { title: string; description?: string; priority?: string; customerId?: string; assignedTo?: string; totalExposure?: number; alertIds?: string[]; tags?: string[] }): Promise<FraudCase> {
    const result = await apiPost<FraudCase>(`${BASE}/cases`, req);
    if (result.error || !result.data) throw new Error(result.error ?? "Failed to create case");
    return result.data;
  },

  async updateCase(id: string, req: Record<string, unknown>): Promise<FraudCase> {
    const result = await apiPut<FraudCase>(`${BASE}/cases/${id}`, req);
    if (result.error || !result.data) throw new Error(result.error ?? "Failed to update case");
    return result.data;
  },

  async addCaseNote(caseId: string, req: { content: string; author: string; noteType?: string }): Promise<CaseNote> {
    const result = await apiPost<CaseNote>(`${BASE}/cases/${caseId}/notes`, req);
    if (result.error || !result.data) throw new Error(result.error ?? "Failed to add note");
    return result.data;
  },

  // ─── Rules ──────────────────────────────────────────────────────────────────
  async listRules(): Promise<FraudRule[]> {
    const result = await apiGet<FraudRule[]>(`${BASE}/rules`);
    if (result.error || !result.data) throw new Error(result.error ?? "Failed to list rules");
    return result.data;
  },

  async updateRule(id: string, req: { severity?: string; enabled?: boolean; parameters?: Record<string, unknown> }): Promise<FraudRule> {
    const result = await apiPut<FraudRule>(`${BASE}/rules/${id}`, req);
    if (result.error || !result.data) throw new Error(result.error ?? "Failed to update rule");
    return result.data;
  },

  // ─── Bulk Operations ────────────────────────────────────────────────────────
  async bulkAssign(alertIds: string[], assignee: string, performedBy: string): Promise<{ assigned: number; total: number }> {
    const result = await apiPut<{ assigned: number; total: number }>(`${BASE}/alerts/bulk/assign?assignee=${assignee}`, { alertIds, performedBy });
    if (result.error || !result.data) throw new Error(result.error ?? "Failed to bulk assign");
    return result.data;
  },

  async bulkResolve(alertIds: string[], confirmedFraud: boolean, performedBy: string, notes?: string): Promise<{ resolved: number; total: number }> {
    const result = await apiPut<{ resolved: number; total: number }>(`${BASE}/alerts/bulk/resolve?confirmedFraud=${confirmedFraud}`, { alertIds, performedBy, notes });
    if (result.error || !result.data) throw new Error(result.error ?? "Failed to bulk resolve");
    return result.data;
  },

  // ─── Network Analysis ──────────────────────────────────────────────────────
  async getCustomerNetwork(customerId: string): Promise<NetworkNode> {
    const result = await apiGet<NetworkNode>(`${BASE}/network/${customerId}`);
    if (result.error || !result.data) throw new Error(result.error ?? "Failed to get network");
    return result.data;
  },

  async getFlaggedClusters(): Promise<NetworkNode[]> {
    const result = await apiGet<NetworkNode[]>(`${BASE}/network/flagged`);
    if (result.error || !result.data) throw new Error(result.error ?? "Failed to get flagged clusters");
    return result.data;
  },

  // ─── Analytics ──────────────────────────────────────────────────────────────
  async getAnalytics(days = 30): Promise<FraudAnalytics> {
    const result = await apiGet<FraudAnalytics>(`${BASE}/analytics?days=${days}`);
    if (result.error || !result.data) throw new Error(result.error ?? "Failed to get analytics");
    return result.data;
  },

  // ─── SAR / CTR Reports ──────────────────────────────────────────────────────
  async listSarReports(page = 0, size = 20, status?: SarStatus, reportType?: SarReportType): Promise<PageResponse<SarReport>> {
    const params = new URLSearchParams({ page: String(page), size: String(size), sort: "createdAt,desc" });
    if (status) params.set("status", status);
    if (reportType) params.set("reportType", reportType);
    const result = await apiGet<PageResponse<SarReport>>(`${BASE}/sar?${params}`);
    if (result.error || !result.data) throw new Error(result.error ?? "Failed to list SAR reports");
    return result.data;
  },

  async getSarReport(id: string): Promise<SarReport> {
    const result = await apiGet<SarReport>(`${BASE}/sar/${id}`);
    if (result.error || !result.data) throw new Error(result.error ?? "Failed to get SAR report");
    return result.data;
  },

  async createSarReport(req: { reportType?: string; subjectCustomerId?: string; subjectName?: string; subjectNationalId?: string; narrative?: string; suspiciousAmount?: number; alertIds?: string[]; caseId?: string; preparedBy?: string }): Promise<SarReport> {
    const result = await apiPost<SarReport>(`${BASE}/sar`, req);
    if (result.error || !result.data) throw new Error(result.error ?? "Failed to create SAR report");
    return result.data;
  },

  async updateSarReport(id: string, req: Record<string, unknown>): Promise<SarReport> {
    const result = await apiPut<SarReport>(`${BASE}/sar/${id}`, req);
    if (result.error || !result.data) throw new Error(result.error ?? "Failed to update SAR report");
    return result.data;
  },

  async generateSarFromCase(caseId: string): Promise<SarReport> {
    const result = await apiPost<SarReport>(`${BASE}/sar/from-case/${caseId}`);
    if (result.error || !result.data) throw new Error(result.error ?? "Failed to generate SAR from case");
    return result.data;
  },

  // ─── Watchlist ──────────────────────────────────────────────────────────────
  async listWatchlistEntries(page = 0, size = 50, active = true): Promise<PageResponse<WatchlistEntry>> {
    const params = new URLSearchParams({ page: String(page), size: String(size), active: String(active) });
    const result = await apiGet<PageResponse<WatchlistEntry>>(`${BASE}/watchlist?${params}`);
    if (result.error || !result.data) throw new Error(result.error ?? "Failed to list watchlist");
    return result.data;
  },

  async createWatchlistEntry(req: { listType: string; entryType: string; name?: string; nationalId?: string; phone?: string; reason?: string; source?: string; expiresAt?: string }): Promise<WatchlistEntry> {
    const result = await apiPost<WatchlistEntry>(`${BASE}/watchlist`, req);
    if (result.error || !result.data) throw new Error(result.error ?? "Failed to create watchlist entry");
    return result.data;
  },

  async deactivateWatchlistEntry(id: string): Promise<void> {
    const result = await apiDelete<void>(`${BASE}/watchlist/${id}`);
    if (result.error) throw new Error(result.error ?? "Failed to deactivate entry");
  },

  // ─── Audit Log ──────────────────────────────────────────────────────────────
  async getAuditLog(page = 0, size = 50, entityType?: string, entityId?: string): Promise<PageResponse<AuditLogEntry>> {
    const params = new URLSearchParams({ page: String(page), size: String(size) });
    if (entityType) params.set("entityType", entityType);
    if (entityId) params.set("entityId", entityId);
    const result = await apiGet<PageResponse<AuditLogEntry>>(`${BASE}/audit-log?${params}`);
    if (result.error || !result.data) throw new Error(result.error ?? "Failed to get audit log");
    return result.data;
  },

  // ─── ML Scoring ────────────────────────────────────────────────────────────
  async scoreTransaction(req: { customerId: string; eventType: string; amount?: number; ruleScore?: number }): Promise<MLScoringResult> {
    const result = await apiPost<MLScoringResult>(`${BASE}/score`, req);
    if (result.error || !result.data) throw new Error(result.error ?? "Failed to score transaction");
    return result.data;
  },

  async getCustomerScoringHistory(customerId: string, page = 0, size = 20): Promise<PageResponse<ScoringHistoryEntry>> {
    const params = new URLSearchParams({ page: String(page), size: String(size) });
    const result = await apiGet<PageResponse<ScoringHistoryEntry>>(`${BASE}/score/customer/${customerId}?${params}`);
    if (result.error || !result.data) throw new Error(result.error ?? "Failed to get scoring history");
    return result.data;
  },

  async getScoringStats(): Promise<ScoringStats> {
    const result = await apiGet<ScoringStats>(`${BASE}/score/stats`);
    if (result.error || !result.data) throw new Error(result.error ?? "Failed to get scoring stats");
    return result.data;
  },

  async getMLHealth(): Promise<MLHealthStatus> {
    const result = await apiGet<MLHealthStatus>(`${BASE}/ml/health`);
    if (result.error || !result.data) throw new Error(result.error ?? "Failed to check ML health");
    return result.data;
  },

  async triggerTraining(modelType: string): Promise<{ status: string; message: string }> {
    const result = await apiPost<{ status: string; message: string }>(`${BASE}/ml/train/${modelType}`);
    if (result.error || !result.data) throw new Error(result.error ?? "Failed to trigger training");
    return result.data;
  },

  async getTrainingStatus(): Promise<TrainingStatus> {
    const result = await apiGet<TrainingStatus>(`${BASE}/ml/train/status`);
    if (result.error || !result.data) throw new Error(result.error ?? "Failed to get training status");
    return result.data;
  },

  // ─── Live Transaction Feed ─────────────────────────────────────────────────
  async getRecentEvents(page = 0, size = 50): Promise<PageResponse<FraudEvent>> {
    const params = new URLSearchParams({ page: String(page), size: String(size), sort: "createdAt,desc" });
    const result = await apiGet<PageResponse<FraudEvent>>(`${BASE}/events/recent?${params}`);
    if (result.error || !result.data) throw new Error(result.error ?? "Failed to get recent events");
    return result.data;
  },

  // ─── Batch Screening ──────────────────────────────────────────────────────
  async triggerBatchScreening(): Promise<BatchScreeningResult> {
    const result = await apiPost<BatchScreeningResult>(`${BASE}/screening/batch`);
    if (result.error || !result.data) throw new Error(result.error ?? "Failed to trigger screening");
    return result.data;
  },

  async screenCustomer(req: { customerId: string; name?: string; nationalId?: string; phone?: string }): Promise<WatchlistEntry[]> {
    const result = await apiPost<WatchlistEntry[]>(`${BASE}/screening/customer`, req);
    if (result.error || !result.data) throw new Error(result.error ?? "Failed to screen customer");
    return result.data;
  },

  // ─── Case Timeline ────────────────────────────────────────────────────────
  async getCaseTimeline(caseId: string): Promise<CaseTimeline> {
    const result = await apiGet<CaseTimeline>(`${BASE}/cases/${caseId}/timeline`);
    if (result.error || !result.data) throw new Error(result.error ?? "Failed to get case timeline");
    return result.data;
  },
};
