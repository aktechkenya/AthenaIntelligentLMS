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
import { accountingService, type JournalEntry } from "@/services/accountingService";
import { Info } from "lucide-react";

const deriveEventLabel = (entry: JournalEntry): string => {
  const desc = entry.description ?? "";
  const evType = entry.sourceEvent ?? "";
  if (desc.toLowerCase().includes("repayment") || evType.toLowerCase().includes("repayment")) {
    return "Loan Repayment Processed";
  }
  if (desc.toLowerCase().includes("disburs") || evType.toLowerCase().includes("disburs")) {
    return "Loan Disbursement";
  }
  if (desc.toLowerCase().includes("float") || evType.toLowerCase().includes("float")) {
    return "Float Transaction";
  }
  if (desc.toLowerCase().includes("fee") || evType.toLowerCase().includes("fee")) {
    return "Fee Charged";
  }
  if (desc.toLowerCase().includes("interest") || evType.toLowerCase().includes("interest")) {
    return "Interest Accrual";
  }
  return entry.description || evType || "Financial Event";
};

const fmt = (n: number) =>
  n === 0
    ? "—"
    : new Intl.NumberFormat("en-KE", { style: "currency", currency: "KES", maximumFractionDigits: 0 }).format(n);

const AuditLogsPage = () => {
  const { data, isLoading, isError } = useQuery({
    queryKey: ["audit-journal-entries"],
    queryFn: () => accountingService.listJournalEntries(0, 100),
  });

  const entries = data?.content ?? [];

  return (
    <DashboardLayout
      title="Audit Logs"
      subtitle="Financial transaction audit trail"
      breadcrumbs={[{ label: "Home", href: "/" }, { label: "Compliance" }, { label: "Audit Logs" }]}
    >
      <div className="space-y-6 animate-fade-in">
        {/* Info banner */}
        <div className="flex items-start gap-3 rounded-lg border border-border bg-muted/40 px-4 py-3">
          <Info className="h-4 w-4 text-muted-foreground mt-0.5 shrink-0" />
          <p className="text-xs text-muted-foreground">
            Showing financial journal audit entries from <span className="font-mono font-medium">accounting-service</span>.
            Each entry represents a posted GL transaction event.
          </p>
        </div>

        {/* Stats */}
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
          <Card>
            <CardContent className="p-5">
              <span className="text-xs text-muted-foreground">Total Entries</span>
              <p className="text-2xl font-heading mt-1">{isLoading ? "—" : entries.length}</p>
            </CardContent>
          </Card>
          <Card>
            <CardContent className="p-5">
              <span className="text-xs text-muted-foreground">Posted</span>
              <p className="text-2xl font-heading mt-1 text-success">
                {isLoading ? "—" : entries.filter((e) => e.status === "POSTED").length}
              </p>
            </CardContent>
          </Card>
          <Card>
            <CardContent className="p-5">
              <span className="text-xs text-muted-foreground">Source</span>
              <p className="text-sm font-medium mt-1 font-mono">accounting-service</p>
            </CardContent>
          </Card>
        </div>

        {isLoading && (
          <Card>
            <CardContent className="flex items-center justify-center py-16 text-muted-foreground text-sm">
              Loading audit entries…
            </CardContent>
          </Card>
        )}

        {isError && (
          <Card>
            <CardContent className="flex items-center justify-center py-16 text-destructive text-sm">
              Failed to load journal entries. Ensure accounting-service is reachable.
            </CardContent>
          </Card>
        )}

        {!isLoading && !isError && (
          <Card>
            <CardHeader className="pb-3">
              <CardTitle className="text-sm font-medium">Journal Entries</CardTitle>
            </CardHeader>
            <CardContent>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead className="text-xs">Timestamp</TableHead>
                    <TableHead className="text-xs">Event</TableHead>
                    <TableHead className="text-xs">Reference</TableHead>
                    <TableHead className="text-xs">Amount</TableHead>
                    <TableHead className="text-xs">System</TableHead>
                    <TableHead className="text-xs">Status</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {entries.length === 0 ? (
                    <TableRow>
                      <TableCell colSpan={6} className="text-center text-muted-foreground py-8 text-sm">
                        No journal entries found.
                      </TableCell>
                    </TableRow>
                  ) : (
                    entries.map((entry) => (
                      <TableRow key={entry.id} className="cursor-pointer">
                        <TableCell className="text-xs font-mono whitespace-nowrap">
                          {new Date(entry.entryDate ?? entry.createdAt ?? "").toLocaleString("en-KE", {
                            dateStyle: "short",
                            timeStyle: "short",
                          })}
                        </TableCell>
                        <TableCell className="text-xs font-medium">{deriveEventLabel(entry)}</TableCell>
                        <TableCell className="text-xs font-mono text-muted-foreground">
                          {entry.sourceId ? entry.sourceId.slice(0, 12) : entry.reference.slice(0, 12)}
                        </TableCell>
                        <TableCell className="text-xs">{fmt(entry.totalDebit)}</TableCell>
                        <TableCell className="text-xs font-mono text-muted-foreground">accounting-service</TableCell>
                        <TableCell>
                          <Badge
                            variant="outline"
                            className="text-[10px] bg-success/10 text-success border-success/20"
                          >
                            {entry.status ?? "POSTED"}
                          </Badge>
                        </TableCell>
                      </TableRow>
                    ))
                  )}
                </TableBody>
              </Table>
            </CardContent>
          </Card>
        )}
      </div>
    </DashboardLayout>
  );
};

export default AuditLogsPage;
