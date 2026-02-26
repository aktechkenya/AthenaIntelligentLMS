import { useMemo } from "react";
import { useNavigate } from "react-router-dom";
import { DashboardLayout } from "@/components/DashboardLayout";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from "@/components/ui/table";
import { AlertTriangle, Users, Clock, Banknote, Eye } from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { loanManagementService, type Loan } from "@/services/loanManagementService";
import { formatKES } from "@/lib/format";

const CollectionsWorkbenchPage = () => {
  const navigate = useNavigate();

  const { data: apiPage, isLoading } = useQuery({
    queryKey: ["collections-loans"],
    queryFn: () => loanManagementService.listLoans(0, 200, "ACTIVE"),
    staleTime: 60_000,
    retry: false,
  });

  const loans: Loan[] = apiPage?.content ?? [];

  const stats = useMemo(() => {
    const totalActive = loans.length;
    const dpdOver0 = loans.filter(l => l.dpd > 0);
    const dpdOver30 = loans.filter(l => l.dpd > 30);
    const totalOutstanding = loans.reduce((s, l) => s + (l.outstandingPrincipal ?? 0), 0);
    return { totalActive, dpdOver0: dpdOver0.length, dpdOver30: dpdOver30.length, totalOutstanding };
  }, [loans]);

  const sorted = useMemo(() => [...loans].sort((a, b) => (b.dpd ?? 0) - (a.dpd ?? 0)), [loans]);

  return (
    <DashboardLayout
      title="Collections Workbench"
      subtitle="Delinquency management and borrower outreach"
      breadcrumbs={[{ label: "Home", href: "/" }, { label: "Collections" }, { label: "Workbench" }]}
    >
      <div className="space-y-4 animate-fade-in">
        {/* KPI Cards */}
        {isLoading ? (
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
            {Array.from({ length: 4 }).map((_, i) => <Skeleton key={i} className="h-24 w-full" />)}
          </div>
        ) : (
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
            <Card>
              <CardContent className="p-5">
                <div className="flex items-center justify-between mb-2">
                  <span className="text-xs text-muted-foreground font-sans">Total Active</span>
                  <Users className="h-4 w-4 text-info" />
                </div>
                <p className="text-2xl font-heading">{stats.totalActive}</p>
              </CardContent>
            </Card>
            <Card>
              <CardContent className="p-5">
                <div className="flex items-center justify-between mb-2">
                  <span className="text-xs text-muted-foreground font-sans">DPD &gt; 0</span>
                  <Clock className="h-4 w-4 text-warning" />
                </div>
                <p className="text-2xl font-heading">{stats.dpdOver0}</p>
              </CardContent>
            </Card>
            <Card>
              <CardContent className="p-5">
                <div className="flex items-center justify-between mb-2">
                  <span className="text-xs text-muted-foreground font-sans">DPD &gt; 30</span>
                  <AlertTriangle className="h-4 w-4 text-destructive" />
                </div>
                <p className="text-2xl font-heading">{stats.dpdOver30}</p>
              </CardContent>
            </Card>
            <Card>
              <CardContent className="p-5">
                <div className="flex items-center justify-between mb-2">
                  <span className="text-xs text-muted-foreground font-sans">Outstanding</span>
                  <Banknote className="h-4 w-4 text-success" />
                </div>
                <p className="text-2xl font-heading">{formatKES(stats.totalOutstanding)}</p>
              </CardContent>
            </Card>
          </div>
        )}

        {/* Loans Table */}
        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="text-sm font-medium">Active Loans — Sorted by DPD</CardTitle>
          </CardHeader>
          <CardContent className="p-0">
            {isLoading ? (
              <div className="p-4 space-y-2">
                {Array.from({ length: 8 }).map((_, i) => <Skeleton key={i} className="h-10 w-full" />)}
              </div>
            ) : sorted.length === 0 ? (
              <div className="flex flex-col items-center justify-center h-48 text-muted-foreground">
                <p className="text-sm font-medium">No active loans</p>
                <p className="text-xs mt-1">No active loans found for collections management.</p>
              </div>
            ) : (
              <Table>
                <TableHeader>
                  <TableRow className="hover:bg-transparent">
                    <TableHead className="text-[10px] uppercase tracking-wider font-sans">Loan ID</TableHead>
                    <TableHead className="text-[10px] uppercase tracking-wider font-sans">Customer</TableHead>
                    <TableHead className="text-[10px] uppercase tracking-wider font-sans text-right">Outstanding</TableHead>
                    <TableHead className="text-[10px] uppercase tracking-wider font-sans text-right">DPD</TableHead>
                    <TableHead className="text-[10px] uppercase tracking-wider font-sans">Status</TableHead>
                    <TableHead className="text-[10px] uppercase tracking-wider font-sans">Next Due</TableHead>
                    <TableHead className="text-[10px] uppercase tracking-wider font-sans">Actions</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {sorted.slice(0, 50).map((loan) => (
                    <TableRow key={loan.id} className="table-row-hover">
                      <TableCell className="text-xs font-mono font-medium">{loan.id?.slice(0, 8)}</TableCell>
                      <TableCell className="text-xs font-sans">{loan.customerId}</TableCell>
                      <TableCell className="text-xs font-mono text-right">{formatKES(loan.outstandingPrincipal ?? 0)}</TableCell>
                      <TableCell className="text-right">
                        <span className={`text-xs font-mono font-bold ${loan.dpd > 30 ? "text-destructive" : loan.dpd > 0 ? "text-warning" : "text-success"}`}>
                          {loan.dpd}
                        </span>
                      </TableCell>
                      <TableCell>
                        <Badge variant="outline" className={`text-[9px] font-sans ${
                          loan.dpd > 90 ? "bg-destructive/15 text-destructive border-destructive/30" :
                          loan.dpd > 30 ? "bg-warning/15 text-warning border-warning/30" :
                          "bg-success/15 text-success border-success/30"
                        }`}>
                          {loan.dpd > 90 ? "NPL" : loan.dpd > 30 ? "Delinquent" : loan.dpd > 0 ? "Watch" : "Current"}
                        </Badge>
                      </TableCell>
                      <TableCell className="text-xs font-sans">{loan.nextDueDate?.split("T")[0] ?? "—"}</TableCell>
                      <TableCell>
                        <Button
                          variant="ghost"
                          size="sm"
                          className="h-7 text-[10px] font-sans"
                          onClick={() => navigate(`/loan/${loan.id}`)}
                        >
                          <Eye className="h-3 w-3 mr-1" /> View
                        </Button>
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

export default CollectionsWorkbenchPage;
