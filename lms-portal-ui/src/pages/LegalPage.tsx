import { DashboardLayout } from "@/components/DashboardLayout";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Scale, FileText, AlertTriangle, DollarSign } from "lucide-react";

const cases = [
  { id: "LEG-001", borrower: "James Otieno", loanId: "LN-2024-0892", amount: 245000, dpd: 180, status: "Demand Letter Sent", assignedTo: "Adv. Kiptoo", lastAction: "2026-02-20" },
  { id: "LEG-002", borrower: "Winnie Chebet", loanId: "LN-2024-0654", amount: 89000, dpd: 210, status: "Court Filing", assignedTo: "Adv. Nyambura", lastAction: "2026-02-18" },
  { id: "LEG-003", borrower: "Moses Wafula", loanId: "LN-2023-1102", amount: 520000, dpd: 365, status: "Write-Off Pending", assignedTo: "Finance Team", lastAction: "2026-02-15" },
  { id: "LEG-004", borrower: "Rose Adhiambo", loanId: "LN-2024-0311", amount: 156000, dpd: 150, status: "Negotiation", assignedTo: "Adv. Kiptoo", lastAction: "2026-02-22" },
  { id: "LEG-005", borrower: "Brian Mutua", loanId: "LN-2024-0478", amount: 380000, dpd: 270, status: "Asset Recovery", assignedTo: "Adv. Otieno", lastAction: "2026-02-10" },
  { id: "LEG-006", borrower: "Nancy Wanjiru", loanId: "LN-2023-0990", amount: 72000, dpd: 400, status: "Written Off", assignedTo: "—", lastAction: "2026-01-30" },
];

const statusStyle: Record<string, string> = {
  "Demand Letter Sent": "bg-warning/10 text-warning border-warning/20",
  "Court Filing": "bg-destructive/10 text-destructive border-destructive/20",
  "Write-Off Pending": "bg-muted text-muted-foreground border-border",
  Negotiation: "bg-info/10 text-info border-info/20",
  "Asset Recovery": "bg-accent/15 text-accent border-accent/20",
  "Written Off": "bg-muted text-muted-foreground border-border",
};

const totalExposure = cases.reduce((s, c) => s + c.amount, 0);
const activeCases = cases.filter((c) => c.status !== "Written Off").length;

const LegalPage = () => {
  return (
    <DashboardLayout
      title="Legal & Write-Offs"
      subtitle="Legal recovery and write-off management"
      breadcrumbs={[{ label: "Home", href: "/" }, { label: "Collections" }, { label: "Legal & Write-Offs" }]}
    >
      <div className="space-y-6 animate-fade-in">
        <div className="grid grid-cols-1 sm:grid-cols-4 gap-4">
          <Card>
            <CardContent className="p-5">
              <div className="flex items-center justify-between mb-2">
                <span className="text-xs text-muted-foreground font-sans">Total Cases</span>
                <Scale className="h-4 w-4 text-muted-foreground" />
              </div>
              <p className="text-2xl font-heading">{cases.length}</p>
            </CardContent>
          </Card>
          <Card>
            <CardContent className="p-5">
              <div className="flex items-center justify-between mb-2">
                <span className="text-xs text-muted-foreground font-sans">Active Cases</span>
                <FileText className="h-4 w-4 text-warning" />
              </div>
              <p className="text-2xl font-heading">{activeCases}</p>
            </CardContent>
          </Card>
          <Card>
            <CardContent className="p-5">
              <div className="flex items-center justify-between mb-2">
                <span className="text-xs text-muted-foreground font-sans">Total Exposure</span>
                <DollarSign className="h-4 w-4 text-destructive" />
              </div>
              <p className="text-2xl font-heading">KES {(totalExposure / 1000).toFixed(0)}K</p>
            </CardContent>
          </Card>
          <Card>
            <CardContent className="p-5">
              <div className="flex items-center justify-between mb-2">
                <span className="text-xs text-muted-foreground font-sans">Avg DPD</span>
                <AlertTriangle className="h-4 w-4 text-warning" />
              </div>
              <p className="text-2xl font-heading">{Math.round(cases.reduce((s, c) => s + c.dpd, 0) / cases.length)}</p>
            </CardContent>
          </Card>
        </div>

        <Card>
          <CardHeader className="pb-3">
            <div className="flex items-center justify-between">
              <CardTitle className="text-sm font-medium">Legal Cases</CardTitle>
              <Button size="sm" variant="outline" className="text-xs">Export</Button>
            </div>
          </CardHeader>
          <CardContent>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="text-xs">Case ID</TableHead>
                  <TableHead className="text-xs">Borrower</TableHead>
                  <TableHead className="text-xs">Loan ID</TableHead>
                  <TableHead className="text-xs text-right">Amount (KES)</TableHead>
                  <TableHead className="text-xs text-right">DPD</TableHead>
                  <TableHead className="text-xs">Assigned To</TableHead>
                  <TableHead className="text-xs">Last Action</TableHead>
                  <TableHead className="text-xs">Status</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {cases.map((c) => (
                  <TableRow key={c.id} className="table-row-hover">
                    <TableCell className="text-xs font-mono">{c.id}</TableCell>
                    <TableCell className="text-sm font-medium">{c.borrower}</TableCell>
                    <TableCell className="text-xs font-mono text-muted-foreground">{c.loanId}</TableCell>
                    <TableCell className="text-xs text-right font-mono">{c.amount.toLocaleString()}</TableCell>
                    <TableCell className="text-xs text-right font-mono">{c.dpd}</TableCell>
                    <TableCell className="text-xs">{c.assignedTo}</TableCell>
                    <TableCell className="text-xs">{c.lastAction}</TableCell>
                    <TableCell>
                      <Badge variant="outline" className={`text-[10px] ${statusStyle[c.status] || ""}`}>
                        {c.status}
                      </Badge>
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

export default LegalPage;
