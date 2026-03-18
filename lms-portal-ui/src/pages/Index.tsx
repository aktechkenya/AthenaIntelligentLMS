import { DashboardLayout } from "@/components/DashboardLayout";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { formatKES } from "@/lib/format";
import { useQuery } from "@tanstack/react-query";
import { reportingService } from "@/services/reportingService";
import { apiGet, type PageResponse } from "@/lib/api";
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  PieChart,
  Pie,
  Cell,
  Legend,
  AreaChart,
  Area,
} from "recharts";

// ─── Types ─────────────────────────────────────────────
interface Loan {
  id: string;
  customerId: string;
  productId: string;
  disbursedAmount: number;
  outstandingPrincipal: number;
  totalOutstanding: number;
  interestRate: number;
  status: string;
  currentDpd?: number;
  currentStage?: string;
  tenorMonths: number;
  disbursedAt: string;
}

interface Product {
  id: string;
  name: string;
  productType: string;
  status: string;
}

interface ComplianceSummary {
  openAlerts: number;
  criticalAlerts: number;
  underReviewAlerts: number;
  sarFiledAlerts: number;
  pendingKyc: number;
  failedKyc: number;
}

// ─── Colors ────────────────────────────────────────────
const COLORS = [
  "hsl(var(--chart-1))",
  "hsl(var(--chart-2))",
  "hsl(var(--chart-3))",
  "hsl(var(--chart-4))",
  "hsl(var(--chart-5))",
];
const FALLBACK_COLORS = ["#3b82f6", "#10b981", "#f59e0b", "#ef4444", "#8b5cf6", "#ec4899"];

const Index = () => {
  // ─── Data Queries ─────────────────────────────────────
  const { data: summary, isLoading } = useQuery({
    queryKey: ["reporting", "summary"],
    queryFn: () => reportingService.getSummary("CURRENT_MONTH"),
    staleTime: 120_000,
    retry: false,
  });

  const { data: loansData } = useQuery({
    queryKey: ["dashboard", "loans"],
    queryFn: async () => {
      const r = await apiGet<PageResponse<Loan>>(
        "/proxy/loans/api/v1/loans?page=0&size=100&status=ACTIVE"
      );
      return r.data;
    },
    staleTime: 120_000,
    retry: false,
  });

  const { data: productsData } = useQuery({
    queryKey: ["dashboard", "products"],
    queryFn: async () => {
      const r = await apiGet<PageResponse<Product>>(
        "/proxy/products/api/v1/products?page=0&size=50"
      );
      return r.data;
    },
    staleTime: 300_000,
    retry: false,
  });

  const { data: complianceData } = useQuery({
    queryKey: ["dashboard", "compliance"],
    queryFn: async () => {
      const r = await apiGet<ComplianceSummary>(
        "/proxy/compliance/api/v1/compliance/summary"
      );
      return r.data;
    },
    staleTime: 120_000,
    retry: false,
  });

  // ─── KPI Cards ────────────────────────────────────────
  const kpis = [
    {
      id: "active-loans",
      title: "Active Loans",
      value: summary?.activeLoans ?? summary?.activeLoanCount,
      format: "number" as const,
      borderColor: "border-l-info",
    },
    {
      id: "loan-book",
      title: "Loan Book (KES)",
      value: summary?.totalDisbursed ?? summary?.disbursedThisMonth,
      format: "currency" as const,
      borderColor: "border-l-primary",
    },
    {
      id: "outstanding",
      title: "Outstanding (KES)",
      value: summary?.totalOutstanding,
      format: "currency" as const,
      borderColor: "border-l-warning",
    },
    {
      id: "collected",
      title: "Collected (KES)",
      value: summary?.totalCollected,
      format: "currency" as const,
      borderColor: "border-l-success",
    },
    {
      id: "par30",
      title: "PAR30 (%)",
      value: summary
        ? summary.parRatio ??
          (summary.par30 != null && summary.totalLoans
            ? (summary.par30 / summary.totalLoans) * 100
            : 0)
        : undefined,
      format: "percent" as const,
      borderColor: "border-l-destructive",
    },
  ];

  function displayValue(kpi: (typeof kpis)[0]): string {
    if (kpi.value == null) return "—";
    const v = typeof kpi.value === "string" ? parseFloat(kpi.value) : kpi.value;
    if (isNaN(v)) return "—";
    if (kpi.format === "currency") return formatKES(v);
    if (kpi.format === "percent") return `${v.toFixed(2)}%`;
    return v.toLocaleString();
  }

  // ─── Chart Data ───────────────────────────────────────
  const loans = loansData?.content ?? [];
  const products = productsData?.content ?? [];

  // Portfolio breakdown by product
  const productLoanMap = new Map<string, { name: string; count: number; amount: number }>();
  for (const l of loans) {
    const prod = products.find((p) => p.id === l.productId);
    const name = prod?.name ?? "Other";
    const existing = productLoanMap.get(name) ?? { name, count: 0, amount: 0 };
    existing.count += 1;
    existing.amount += Number(l.disbursedAmount) || 0;
    productLoanMap.set(name, existing);
  }
  const portfolioBreakdown = Array.from(productLoanMap.values());

  // Outstanding by loan
  const loanOutstanding = loans
    .map((l) => ({
      name: (l.customerId ?? "").slice(0, 12),
      outstanding: Number(l.totalOutstanding) || Number(l.outstandingPrincipal) || 0,
      disbursed: Number(l.disbursedAmount) || 0,
    }))
    .sort((a, b) => b.outstanding - a.outstanding)
    .slice(0, 10);

  // Compliance overview
  const complianceChart = complianceData
    ? [
        { name: "Open Alerts", value: complianceData.openAlerts || 0 },
        { name: "Under Review", value: complianceData.underReviewAlerts || 0 },
        { name: "SAR Filed", value: complianceData.sarFiledAlerts || 0 },
        { name: "Pending KYC", value: complianceData.pendingKyc || 0 },
        { name: "Failed KYC", value: complianceData.failedKyc || 0 },
      ].filter((d) => d.value > 0)
    : [];

  // Collection stages from summary
  const collectionStages = summary
    ? [
        { name: "Performing", value: (summary.activeLoans ?? 0) - (summary.watchLoans ?? 0) - (summary.substandardLoans ?? 0) - (summary.doubtfulLoans ?? 0) - (summary.lossLoans ?? 0) },
        { name: "Watch", value: summary.watchLoans ?? 0 },
        { name: "Substandard", value: summary.substandardLoans ?? 0 },
        { name: "Doubtful", value: summary.doubtfulLoans ?? 0 },
        { name: "Loss", value: summary.lossLoans ?? 0 },
      ].filter((d) => d.value > 0)
    : [];

  return (
    <DashboardLayout
      title="Overview Dashboard"
      subtitle="Real-time portfolio overview & key metrics"
    >
      <div className="space-y-6">
        {/* ─── KPI Cards ─── */}
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-4">
          {kpis.map((kpi) => (
            <Card
              key={kpi.id}
              className={`border-l-4 ${kpi.borderColor} hover:shadow-md transition-shadow`}
            >
              <CardContent className="p-4">
                <p className="text-[11px] text-muted-foreground font-sans uppercase tracking-wider mb-1">
                  {kpi.title}
                </p>
                {isLoading ? (
                  <Skeleton className="h-7 w-24 mt-1" />
                ) : (
                  <p className="text-xl font-mono font-bold mt-1">
                    {displayValue(kpi)}
                  </p>
                )}
              </CardContent>
            </Card>
          ))}
        </div>

        {/* ─── Charts Row 1 ─── */}
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          {/* Portfolio by Product */}
          <Card>
            <CardHeader className="pb-2">
              <CardTitle className="text-sm font-semibold">Portfolio by Product</CardTitle>
            </CardHeader>
            <CardContent>
              {portfolioBreakdown.length > 0 ? (
                <ResponsiveContainer width="100%" height={250}>
                  <PieChart>
                    <Pie
                      data={portfolioBreakdown}
                      dataKey="amount"
                      nameKey="name"
                      cx="50%"
                      cy="50%"
                      innerRadius={50}
                      outerRadius={90}
                      paddingAngle={2}
                      label={({ name, percent }) =>
                        `${name.split(" ")[0]} ${(percent * 100).toFixed(0)}%`
                      }
                      labelLine={false}
                    >
                      {portfolioBreakdown.map((_, i) => (
                        <Cell key={i} fill={FALLBACK_COLORS[i % FALLBACK_COLORS.length]} />
                      ))}
                    </Pie>
                    <Tooltip
                      formatter={(value: number) => formatKES(value)}
                    />
                    <Legend
                      verticalAlign="bottom"
                      height={36}
                      formatter={(value) => (
                        <span className="text-xs">{value}</span>
                      )}
                    />
                  </PieChart>
                </ResponsiveContainer>
              ) : (
                <div className="flex items-center justify-center h-[250px] text-muted-foreground text-sm">
                  No active loans yet
                </div>
              )}
            </CardContent>
          </Card>

          {/* Outstanding by Borrower */}
          <Card>
            <CardHeader className="pb-2">
              <CardTitle className="text-sm font-semibold">Outstanding by Borrower</CardTitle>
            </CardHeader>
            <CardContent>
              {loanOutstanding.length > 0 ? (
                <ResponsiveContainer width="100%" height={250}>
                  <BarChart data={loanOutstanding} layout="vertical" margin={{ left: 10 }}>
                    <CartesianGrid strokeDasharray="3 3" opacity={0.3} />
                    <XAxis type="number" tickFormatter={(v) => `${(v / 1000).toFixed(0)}K`} fontSize={11} />
                    <YAxis type="category" dataKey="name" width={95} fontSize={10} />
                    <Tooltip formatter={(v: number) => formatKES(v)} />
                    <Bar dataKey="outstanding" name="Outstanding" fill="#f59e0b" radius={[0, 4, 4, 0]} />
                    <Bar dataKey="disbursed" name="Disbursed" fill="#3b82f6" radius={[0, 4, 4, 0]} opacity={0.4} />
                  </BarChart>
                </ResponsiveContainer>
              ) : (
                <div className="flex items-center justify-center h-[250px] text-muted-foreground text-sm">
                  No loan data
                </div>
              )}
            </CardContent>
          </Card>
        </div>

        {/* ─── Charts Row 2 ─── */}
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          {/* Loan Staging / DPD Buckets */}
          <Card>
            <CardHeader className="pb-2">
              <CardTitle className="text-sm font-semibold">Loan Staging</CardTitle>
            </CardHeader>
            <CardContent>
              {collectionStages.length > 0 ? (
                <ResponsiveContainer width="100%" height={220}>
                  <PieChart>
                    <Pie
                      data={collectionStages}
                      dataKey="value"
                      nameKey="name"
                      cx="50%"
                      cy="50%"
                      outerRadius={75}
                      label={({ name, value }) => `${name}: ${value}`}
                      labelLine={false}
                    >
                      {collectionStages.map((_, i) => (
                        <Cell
                          key={i}
                          fill={
                            ["#10b981", "#f59e0b", "#f97316", "#ef4444", "#7f1d1d"][i] ??
                            FALLBACK_COLORS[i]
                          }
                        />
                      ))}
                    </Pie>
                    <Tooltip />
                  </PieChart>
                </ResponsiveContainer>
              ) : (
                <div className="flex items-center justify-center h-[220px] text-muted-foreground text-sm">
                  All loans performing
                </div>
              )}
            </CardContent>
          </Card>

          {/* Compliance Overview */}
          <Card>
            <CardHeader className="pb-2">
              <CardTitle className="text-sm font-semibold">Compliance & AML</CardTitle>
            </CardHeader>
            <CardContent>
              {complianceChart.length > 0 ? (
                <ResponsiveContainer width="100%" height={220}>
                  <BarChart data={complianceChart}>
                    <CartesianGrid strokeDasharray="3 3" opacity={0.3} />
                    <XAxis dataKey="name" fontSize={10} angle={-20} textAnchor="end" height={50} />
                    <YAxis fontSize={11} allowDecimals={false} />
                    <Tooltip />
                    <Bar dataKey="value" name="Count" fill="#ef4444" radius={[4, 4, 0, 0]} />
                  </BarChart>
                </ResponsiveContainer>
              ) : (
                <div className="flex items-center justify-center h-[220px] text-muted-foreground text-sm">
                  No compliance alerts
                </div>
              )}
            </CardContent>
          </Card>

          {/* Portfolio Summary */}
          <Card>
            <CardHeader className="pb-2">
              <CardTitle className="text-sm font-semibold">Portfolio Health</CardTitle>
            </CardHeader>
            <CardContent>
              <div className="space-y-4 pt-2">
                <div className="flex justify-between items-center">
                  <span className="text-xs text-muted-foreground">Total Loans</span>
                  <span className="font-mono font-bold">{summary?.totalLoans ?? 0}</span>
                </div>
                <div className="flex justify-between items-center">
                  <span className="text-xs text-muted-foreground">Active</span>
                  <span className="font-mono font-bold text-green-600">{summary?.activeLoans ?? 0}</span>
                </div>
                <div className="flex justify-between items-center">
                  <span className="text-xs text-muted-foreground">Closed</span>
                  <span className="font-mono">{summary?.closedLoans ?? 0}</span>
                </div>
                <div className="flex justify-between items-center">
                  <span className="text-xs text-muted-foreground">Defaulted</span>
                  <span className="font-mono text-red-600">{summary?.defaultedLoans ?? 0}</span>
                </div>
                <hr className="border-dashed" />
                <div className="flex justify-between items-center">
                  <span className="text-xs text-muted-foreground">Collection Rate</span>
                  <span className="font-mono font-bold">
                    {summary && Number(summary.totalDisbursed) > 0
                      ? `${((Number(summary.totalCollected) / Number(summary.totalDisbursed)) * 100).toFixed(1)}%`
                      : "—"}
                  </span>
                </div>
                <div className="flex justify-between items-center">
                  <span className="text-xs text-muted-foreground">Compliance Alerts</span>
                  <span className="font-mono text-amber-600">{complianceData?.openAlerts ?? 0}</span>
                </div>
              </div>
            </CardContent>
          </Card>
        </div>
      </div>
    </DashboardLayout>
  );
};

export default Index;
