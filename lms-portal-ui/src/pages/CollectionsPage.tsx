import { useQuery } from "@tanstack/react-query";
import { DashboardLayout } from "@/components/DashboardLayout";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { loanManagementService } from "@/services/loanManagementService";
import { AlertTriangle, CheckCircle, Users } from "lucide-react";

const dpdBadge = (dpd: number) => {
  if (dpd > 60) return <Badge className="bg-red-100 text-red-700 border-red-200 text-[10px]">DPD {dpd} — Critical</Badge>;
  if (dpd > 30) return <Badge className="bg-orange-100 text-orange-700 border-orange-200 text-[10px]">DPD {dpd} — High</Badge>;
  return <Badge className="bg-yellow-100 text-yellow-700 border-yellow-200 text-[10px]">DPD {dpd} — Watch</Badge>;
};

const fmt = (n: number) =>
  new Intl.NumberFormat("en-KE", { style: "currency", currency: "KES", maximumFractionDigits: 0 }).format(n);

const CollectionsPage = () => {
  const { data, isLoading, isError } = useQuery({
    queryKey: ["collections-loans"],
    queryFn: () => loanManagementService.listLoans(0, 100, "ACTIVE"),
  });

  const allActive = data?.content ?? [];
  const delinquent = allActive.filter((l) => (l.dpd ?? 0) > 0);
  const onTrack = allActive.length - delinquent.length;

  return (
    <DashboardLayout
      title="Collections Queue"
      subtitle="Delinquency management"
      breadcrumbs={[{ label: "Home", href: "/" }, { label: "Collections" }]}
    >
      <div className="space-y-6 animate-fade-in">
        {/* Summary stats */}
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
          <Card>
            <CardContent className="p-5">
              <div className="flex items-center justify-between mb-2">
                <span className="text-xs text-muted-foreground font-sans">Total Active Loans</span>
                <Users className="h-4 w-4 text-muted-foreground" />
              </div>
              <p className="text-2xl font-heading">{isLoading ? "—" : allActive.length}</p>
            </CardContent>
          </Card>
          <Card>
            <CardContent className="p-5">
              <div className="flex items-center justify-between mb-2">
                <span className="text-xs text-muted-foreground font-sans">Delinquent</span>
                <AlertTriangle className="h-4 w-4 text-destructive" />
              </div>
              <p className="text-2xl font-heading text-destructive">{isLoading ? "—" : delinquent.length}</p>
            </CardContent>
          </Card>
          <Card>
            <CardContent className="p-5">
              <div className="flex items-center justify-between mb-2">
                <span className="text-xs text-muted-foreground font-sans">On Track</span>
                <CheckCircle className="h-4 w-4 text-success" />
              </div>
              <p className="text-2xl font-heading text-success">{isLoading ? "—" : onTrack}</p>
            </CardContent>
          </Card>
        </div>

        {/* Main content */}
        {isLoading && (
          <Card>
            <CardContent className="flex items-center justify-center py-16 text-muted-foreground text-sm">
              Loading collections data…
            </CardContent>
          </Card>
        )}

        {isError && (
          <Card>
            <CardContent className="flex items-center justify-center py-16 text-destructive text-sm">
              Failed to load loan data. Ensure loan-management-service is reachable.
            </CardContent>
          </Card>
        )}

        {!isLoading && !isError && delinquent.length === 0 && (
          <Card>
            <CardContent className="flex flex-col items-center justify-center py-16 gap-3">
              <CheckCircle className="h-10 w-10 text-success" />
              <p className="text-base font-medium">0 Delinquent Accounts</p>
              <p className="text-sm text-muted-foreground">All active loans are current. No collections action required.</p>
            </CardContent>
          </Card>
        )}

        {!isLoading && !isError && delinquent.length > 0 && (
          <Card>
            <CardHeader className="pb-3">
              <CardTitle className="text-sm font-medium flex items-center gap-2">
                Delinquent Accounts
                <Badge variant="destructive" className="text-[10px]">{delinquent.length}</Badge>
              </CardTitle>
            </CardHeader>
            <CardContent>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead className="text-xs">Customer ID</TableHead>
                    <TableHead className="text-xs">Loan ID</TableHead>
                    <TableHead className="text-xs">DPD</TableHead>
                    <TableHead className="text-xs">Outstanding</TableHead>
                    <TableHead className="text-xs">Status</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {delinquent.map((loan) => (
                    <TableRow key={loan.id} className="cursor-pointer">
                      <TableCell className="text-xs font-mono">{loan.customerId}</TableCell>
                      <TableCell className="text-xs font-mono">{loan.id.slice(0, 8)}</TableCell>
                      <TableCell className="text-xs">{loan.dpd}</TableCell>
                      <TableCell className="text-xs">{fmt(loan.outstandingPrincipal)}</TableCell>
                      <TableCell>{dpdBadge(loan.dpd)}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </CardContent>
          </Card>
        )}
      </div>
    </DashboardLayout>
  );
};

export default CollectionsPage;
