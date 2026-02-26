import { DashboardLayout } from "@/components/DashboardLayout";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { Shield, AlertTriangle } from "lucide-react";
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from "@/components/ui/table";
import { useQuery } from "@tanstack/react-query";
import { complianceService, type ComplianceAlert } from "@/services/complianceService";

const riskColor: Record<string, string> = {
  HIGH: "bg-destructive/10 text-destructive border-destructive/20",
  MEDIUM: "bg-warning/10 text-warning border-warning/20",
  LOW: "bg-info/10 text-info border-info/20",
};

const CompliancePage = () => {
  const { data: page, isLoading, isError } = useQuery({
    queryKey: ["compliance", "alerts"],
    queryFn: () => complianceService.listAlerts(0, 50),
    staleTime: 60_000,
    retry: false,
  });

  const alerts: ComplianceAlert[] = page?.content ?? [];

  return (
    <DashboardLayout
      title="KYC & Compliance"
      subtitle="Customer verification & regulatory checks"
    >
      <div className="space-y-4 animate-fade-in">
        {/* Summary */}
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
          <Card>
            <CardContent className="p-5 flex items-center gap-3">
              <div className="h-10 w-10 rounded-lg bg-primary/10 flex items-center justify-center">
                <Shield className="h-5 w-5 text-primary" />
              </div>
              <div>
                <p className="text-xs text-muted-foreground">Total Alerts</p>
                {isLoading ? (
                  <Skeleton className="h-7 w-10 mt-1" />
                ) : (
                  <p className="text-xl font-bold">{page?.totalElements ?? alerts.length}</p>
                )}
              </div>
            </CardContent>
          </Card>
          <Card>
            <CardContent className="p-5 flex items-center gap-3">
              <div className="h-10 w-10 rounded-lg bg-destructive/10 flex items-center justify-center">
                <AlertTriangle className="h-5 w-5 text-destructive" />
              </div>
              <div>
                <p className="text-xs text-muted-foreground">High Risk</p>
                {isLoading ? (
                  <Skeleton className="h-7 w-10 mt-1" />
                ) : (
                  <p className="text-xl font-bold">
                    {alerts.filter((a) => a.riskLevel === "HIGH").length}
                  </p>
                )}
              </div>
            </CardContent>
          </Card>
          <Card>
            <CardContent className="p-5 flex items-center gap-3">
              <div className="h-10 w-10 rounded-lg bg-warning/10 flex items-center justify-center">
                <AlertTriangle className="h-5 w-5 text-warning" />
              </div>
              <div>
                <p className="text-xs text-muted-foreground">Pending Review</p>
                {isLoading ? (
                  <Skeleton className="h-7 w-10 mt-1" />
                ) : (
                  <p className="text-xl font-bold">
                    {alerts.filter((a) => a.status === "PENDING" || a.status === "OPEN").length}
                  </p>
                )}
              </div>
            </CardContent>
          </Card>
        </div>

        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="text-sm font-medium">Compliance Alerts</CardTitle>
          </CardHeader>
          <CardContent className="p-0">
            {isLoading ? (
              <div className="p-4 space-y-2">
                {Array.from({ length: 4 }).map((_, i) => (
                  <Skeleton key={i} className="h-10 w-full" />
                ))}
              </div>
            ) : isError ? (
              <div className="flex flex-col items-center justify-center h-48 text-muted-foreground">
                <p className="text-sm font-medium">Unable to load compliance data</p>
                <p className="text-xs mt-1">Compliance service returned an error.</p>
              </div>
            ) : alerts.length === 0 ? (
              <div className="flex flex-col items-center justify-center h-48 text-muted-foreground">
                <Shield className="h-10 w-10 mb-3 opacity-30" />
                <p className="text-sm font-medium">No compliance alerts</p>
                <p className="text-xs mt-1">All compliance checks are clear.</p>
              </div>
            ) : (
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead className="text-xs">Alert ID</TableHead>
                    <TableHead className="text-xs">Entity</TableHead>
                    <TableHead className="text-xs">Type</TableHead>
                    <TableHead className="text-xs">Risk Level</TableHead>
                    <TableHead className="text-xs">Status</TableHead>
                    <TableHead className="text-xs">Date</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {alerts.map((alert) => (
                    <TableRow key={alert.id} className="table-row-hover">
                      <TableCell className="text-xs font-mono">{alert.id}</TableCell>
                      <TableCell className="text-xs">{alert.entityId ?? "—"}</TableCell>
                      <TableCell className="text-xs">{alert.alertType ?? alert.checkType ?? "—"}</TableCell>
                      <TableCell>
                        {alert.riskLevel ? (
                          <Badge variant="outline" className={`text-[10px] ${riskColor[alert.riskLevel] ?? ""}`}>
                            {alert.riskLevel}
                          </Badge>
                        ) : (
                          <span className="text-xs text-muted-foreground">—</span>
                        )}
                      </TableCell>
                      <TableCell>
                        <Badge variant="outline" className="text-[10px]">{alert.status}</Badge>
                      </TableCell>
                      <TableCell className="text-xs text-muted-foreground">
                        {alert.checkedAt ?? alert.createdAt ?? "—"}
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

export default CompliancePage;
