import { DashboardLayout } from "@/components/DashboardLayout";
import { Card, CardContent } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { formatKES } from "@/lib/format";
import { useQuery } from "@tanstack/react-query";
import { reportingService } from "@/services/reportingService";

const Index = () => {
  const { data: summary, isLoading } = useQuery({
    queryKey: ["reporting", "summary"],
    queryFn: () => reportingService.getSummary("CURRENT_MONTH"),
    staleTime: 120_000,
    retry: false,
  });

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
      value:
        summary
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
    if (kpi.format === "currency") return formatKES(kpi.value);
    if (kpi.format === "percent") return `${kpi.value.toFixed(2)}%`;
    return kpi.value.toLocaleString();
  }

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

        {/* ─── Analytics Placeholder ─── */}
        <Card>
          <CardContent className="flex flex-col items-center justify-center h-48 text-muted-foreground">
            <p className="text-sm font-medium">Analytics charts</p>
            <p className="text-xs mt-1 text-center max-w-sm">
              Connect an analytics service to populate origination trends,
              portfolio breakdown, DPD buckets, and AI model health.
            </p>
          </CardContent>
        </Card>
      </div>
    </DashboardLayout>
  );
};

export default Index;
