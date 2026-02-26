import { DashboardLayout } from "@/components/DashboardLayout";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { FileWarning, ShieldCheck } from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { complianceService } from "@/services/complianceService";

const SARReportsPage = () => {
  const { data: apiPage, isLoading } = useQuery({
    queryKey: ["sar-compliance"],
    queryFn: () => complianceService.listAlerts(0, 1),
    staleTime: 60_000,
    retry: false,
  });

  const totalChecks = apiPage?.totalElements ?? 0;

  return (
    <DashboardLayout
      title="SAR Reports"
      subtitle="Suspicious activity reports"
      breadcrumbs={[{ label: "Home", href: "/" }, { label: "Compliance" }, { label: "SAR / CTR Reports" }]}
    >
      <div className="space-y-6 animate-fade-in">
        <div className="flex items-center gap-3">
          <Badge className="bg-success/10 text-success border-success/20 gap-1.5 px-3 py-1">
            <span className="h-1.5 w-1.5 rounded-full bg-success inline-block animate-pulse" />
            Monitoring Active
          </Badge>
          <span className="text-xs text-muted-foreground">
            Compliance service is monitoring {isLoading ? "..." : totalChecks} transaction checks
          </span>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <Card>
            <CardContent className="p-5">
              <div className="flex items-center justify-between mb-2">
                <span className="text-xs text-muted-foreground font-sans">Compliance Checks</span>
                <ShieldCheck className="h-4 w-4 text-info" />
              </div>
              {isLoading ? (
                <Skeleton className="h-8 w-16" />
              ) : (
                <p className="text-2xl font-heading">{totalChecks}</p>
              )}
            </CardContent>
          </Card>
          <Card>
            <CardContent className="p-5">
              <div className="flex items-center justify-between mb-2">
                <span className="text-xs text-muted-foreground font-sans">SAR Filings</span>
                <FileWarning className="h-4 w-4 text-warning" />
              </div>
              <p className="text-2xl font-heading">0</p>
            </CardContent>
          </Card>
        </div>

        <Card>
          <CardHeader className="pb-3">
            <CardTitle className="text-sm font-medium">Suspicious Activity Reports</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="flex flex-col items-center justify-center h-48 text-muted-foreground">
              <FileWarning className="h-8 w-8 mb-2 text-muted-foreground/50" />
              <p className="text-sm font-medium">No SAR filings required</p>
              <p className="text-xs mt-1">
                When compliance alerts require regulatory filing, SAR reports will be generated here.
              </p>
              <p className="text-[10px] mt-3 text-muted-foreground/70">
                The AML monitoring system automatically flags transactions that may require SAR filing.
              </p>
            </div>
          </CardContent>
        </Card>
      </div>
    </DashboardLayout>
  );
};

export default SARReportsPage;
