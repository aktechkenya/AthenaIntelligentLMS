import { DashboardLayout } from "@/components/DashboardLayout";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { RefreshCw, TrendingUp, Plus, CheckCircle } from "lucide-react";
import { Button } from "@/components/ui/button";

const modifications = [
  { id: "MOD-001", loanId: "LN-2025-0012", borrower: "Jane Wanjiku", type: "Rate Change", from: "18%", to: "15%", status: "Approved", requestedBy: "Sarah Kamau", date: "2026-02-22" },
  { id: "MOD-002", loanId: "LN-2025-0018", borrower: "Peter Ochieng", type: "Top-Up", from: "KES 200K", to: "KES 350K", status: "Pending", requestedBy: "Lucy Njeri", date: "2026-02-24" },
  { id: "MOD-003", loanId: "LN-2024-0311", borrower: "Rose Adhiambo", type: "Reschedule", from: "12 months", to: "18 months", status: "Approved", requestedBy: "Peter Oloo", date: "2026-02-20" },
  { id: "MOD-004", loanId: "LN-2025-0045", borrower: "Mary Akinyi", type: "Rate Change", from: "22%", to: "19%", status: "Rejected", requestedBy: "Sarah Kamau", date: "2026-02-19" },
  { id: "MOD-005", loanId: "LN-2025-0033", borrower: "Grace Muthoni", type: "Top-Up", from: "KES 150K", to: "KES 250K", status: "Pending", requestedBy: "Lucy Njeri", date: "2026-02-23" },
];

const typeStyle: Record<string, string> = {
  "Rate Change": "bg-info/10 text-info border-info/20",
  "Top-Up": "bg-accent/15 text-accent border-accent/20",
  Reschedule: "bg-warning/10 text-warning border-warning/20",
};

const statusStyle: Record<string, string> = {
  Approved: "bg-success/10 text-success border-success/20",
  Pending: "bg-warning/10 text-warning border-warning/20",
  Rejected: "bg-destructive/10 text-destructive border-destructive/20",
};

const ModificationsPage = () => {
  return (
    <DashboardLayout
      title="Loan Modifications"
      subtitle="Reschedules, top-ups, and rate changes"
      breadcrumbs={[{ label: "Home", href: "/" }, { label: "Lending" }, { label: "Loan Modifications" }]}
    >
      <div className="space-y-6 animate-fade-in">
        <div className="grid grid-cols-1 sm:grid-cols-4 gap-4">
          <Card>
            <CardContent className="p-5">
              <div className="flex items-center justify-between mb-2">
                <span className="text-xs text-muted-foreground font-sans">Total Requests</span>
                <RefreshCw className="h-4 w-4 text-muted-foreground" />
              </div>
              <p className="text-2xl font-heading">{modifications.length}</p>
            </CardContent>
          </Card>
          <Card>
            <CardContent className="p-5">
              <div className="flex items-center justify-between mb-2">
                <span className="text-xs text-muted-foreground font-sans">Pending</span>
                <RefreshCw className="h-4 w-4 text-warning" />
              </div>
              <p className="text-2xl font-heading">{modifications.filter((m) => m.status === "Pending").length}</p>
            </CardContent>
          </Card>
          <Card>
            <CardContent className="p-5">
              <div className="flex items-center justify-between mb-2">
                <span className="text-xs text-muted-foreground font-sans">Approved</span>
                <CheckCircle className="h-4 w-4 text-success" />
              </div>
              <p className="text-2xl font-heading">{modifications.filter((m) => m.status === "Approved").length}</p>
            </CardContent>
          </Card>
          <Card>
            <CardContent className="p-5">
              <div className="flex items-center justify-between mb-2">
                <span className="text-xs text-muted-foreground font-sans">Top-Ups</span>
                <TrendingUp className="h-4 w-4 text-accent" />
              </div>
              <p className="text-2xl font-heading">{modifications.filter((m) => m.type === "Top-Up").length}</p>
            </CardContent>
          </Card>
        </div>

        <Card>
          <CardHeader className="pb-3">
            <div className="flex items-center justify-between">
              <CardTitle className="text-sm font-medium">Modification Requests</CardTitle>
              <Button size="sm" className="text-xs gap-1.5"><Plus className="h-3.5 w-3.5" /> New Request</Button>
            </div>
          </CardHeader>
          <CardContent>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="text-xs">ID</TableHead>
                  <TableHead className="text-xs">Loan ID</TableHead>
                  <TableHead className="text-xs">Borrower</TableHead>
                  <TableHead className="text-xs">Type</TableHead>
                  <TableHead className="text-xs">From</TableHead>
                  <TableHead className="text-xs">To</TableHead>
                  <TableHead className="text-xs">Requested By</TableHead>
                  <TableHead className="text-xs">Date</TableHead>
                  <TableHead className="text-xs">Status</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {modifications.map((m) => (
                  <TableRow key={m.id} className="table-row-hover">
                    <TableCell className="text-xs font-mono">{m.id}</TableCell>
                    <TableCell className="text-xs font-mono text-muted-foreground">{m.loanId}</TableCell>
                    <TableCell className="text-sm font-medium">{m.borrower}</TableCell>
                    <TableCell>
                      <Badge variant="outline" className={`text-[10px] ${typeStyle[m.type] || ""}`}>{m.type}</Badge>
                    </TableCell>
                    <TableCell className="text-xs font-mono">{m.from}</TableCell>
                    <TableCell className="text-xs font-mono font-medium">{m.to}</TableCell>
                    <TableCell className="text-xs">{m.requestedBy}</TableCell>
                    <TableCell className="text-xs">{m.date}</TableCell>
                    <TableCell>
                      <Badge variant="outline" className={`text-[10px] ${statusStyle[m.status] || ""}`}>{m.status}</Badge>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </CardContent>
        </Card>
      </div>
    </DashboardLayout>
  );
};

export default ModificationsPage;
