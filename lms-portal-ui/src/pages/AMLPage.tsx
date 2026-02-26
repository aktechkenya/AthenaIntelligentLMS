import { DashboardLayout } from "@/components/DashboardLayout";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from "@/components/ui/table";
import { ShieldCheck, Activity, AlertTriangle } from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { complianceService, type ComplianceAlert } from "@/services/complianceService";

const AMLPage = () => {
  const { data: apiPage, isLoading } = useQuery({
    queryKey: ["aml-alerts"],
    queryFn: () => complianceService.listAlerts(0, 50),
    staleTime: 60_000,
    retry: false,
  });

  const alerts: ComplianceAlert[] = apiPage?.content ?? [];
  const amlAlerts = alerts.filter(
    (a) => a.alertType?.toLowerCase().includes("aml") || a.checkType?.toLowerCase().includes("aml")
  );
  const displayAlerts = amlAlerts.length > 0 ? amlAlerts : alerts;
  const totalAlerts = apiPage?.totalElements ?? 0;

  return (
    <DashboardLayout
      title="AML Monitoring"
      subtitle="Anti-money laundering alerts and investigations"
      breadcrumbs={[{ label: "Home", href: "/" }, { label: "Compliance" }, { label: "AML Monitoring" }]}
    >
      <div className="space-y-6 animate-fade-in">
        <div className="flex items-center gap-3">
          <Badge className="bg-success/10 text-success border-success/20 gap-1.5 px-3 py-1">
            <span className="h-1.5 w-1.5 rounded-full bg-success inline-block animate-pulse" />
            System Active
          </Badge>
          <span className="text-xs text-muted-foreground">
            Real-time AML screening active via compliance-service
          </span>
        </div>

        {isLoading ? (
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
            {Array.from({ length: 3 }).map((_, i) => <Skeleton key={i} className="h-20 w-full" />)}
          </div>
        ) : (
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
            <Card>
              <CardContent className="p-5">
                <div className="flex items-center justify-between mb-2">
                  <span className="text-xs text-muted-foreground font-sans">Total Checks</span>
                  <Activity className="h-4 w-4 text-muted-foreground" />
                </div>
                <p className="text-2xl font-heading">{totalAlerts}</p>
              </CardContent>
            </Card>
            <Card>
              <CardContent className="p-5">
                <div className="flex items-center justify-between mb-2">
                  <span className="text-xs text-muted-foreground font-sans">AML Alerts</span>
                  <AlertTriangle className="h-4 w-4 text-muted-foreground" />
                </div>
                <p className="text-2xl font-heading">{amlAlerts.length}</p>
              </CardContent>
            </Card>
            <Card>
              <CardContent className="p-5">
                <div className="flex items-center justify-between mb-2">
                  <span className="text-xs text-muted-foreground font-sans">Under Review</span>
                  <ShieldCheck className="h-4 w-4 text-muted-foreground" />
                </div>
                <p className="text-2xl font-heading">{alerts.filter(a => a.status === "UNDER_REVIEW" || a.status === "PENDING").length}</p>
              </CardContent>
            </Card>
          </div>
        )}

        <Card>
          <CardHeader className="pb-3">
            <CardTitle className="text-sm font-medium">AML Alerts</CardTitle>
          </CardHeader>
          <CardContent className="p-0">
            {isLoading ? (
              <div className="p-4 space-y-2">
                {Array.from({ length: 5 }).map((_, i) => <Skeleton key={i} className="h-10 w-full" />)}
              </div>
            ) : displayAlerts.length === 0 ? (
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead className="text-xs">Transaction ID</TableHead>
                    <TableHead className="text-xs">Customer</TableHead>
                    <TableHead className="text-xs">Flag Reason</TableHead>
                    <TableHead className="text-xs">Risk Score</TableHead>
                    <TableHead className="text-xs">Status</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  <TableRow>
                    <TableCell colSpan={5} className="text-center text-muted-foreground py-8 text-sm">
                      No AML alerts detected. System monitoring is active.
                    </TableCell>
                  </TableRow>
                </TableBody>
              </Table>
            ) : (
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead className="text-xs">Alert ID</TableHead>
                    <TableHead className="text-xs">Type</TableHead>
                    <TableHead className="text-xs">Entity</TableHead>
                    <TableHead className="text-xs">Risk Level</TableHead>
                    <TableHead className="text-xs">Findings</TableHead>
                    <TableHead className="text-xs">Date</TableHead>
                    <TableHead className="text-xs">Status</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {displayAlerts.map((a) => (
                    <TableRow key={a.id} className="table-row-hover">
                      <TableCell className="text-xs font-mono">{a.id?.slice(0, 8)}</TableCell>
                      <TableCell className="text-xs">{a.alertType ?? a.checkType ?? "—"}</TableCell>
                      <TableCell className="text-xs font-mono">{a.entityId ?? "—"}</TableCell>
                      <TableCell>
                        <Badge variant="outline" className={`text-[10px] ${
                          a.riskLevel === "HIGH" ? "bg-destructive/15 text-destructive border-destructive/30" :
                          a.riskLevel === "MEDIUM" ? "bg-warning/15 text-warning border-warning/30" :
                          "bg-success/15 text-success border-success/30"
                        }`}>
                          {a.riskLevel ?? "—"}
                        </Badge>
                      </TableCell>
                      <TableCell className="text-xs text-muted-foreground max-w-[200px] truncate">{a.findings ?? "—"}</TableCell>
                      <TableCell className="text-xs">{a.createdAt?.split("T")[0] ?? "—"}</TableCell>
                      <TableCell>
                        <Badge variant="outline" className="text-[10px]">{a.status}</Badge>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            )}
          </CardContent>
        </Card>
      </div>
    </DashboardLayout>
  );
};

export default AMLPage;
