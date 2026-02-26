import { DashboardLayout } from "@/components/DashboardLayout";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Separator } from "@/components/ui/separator";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { useQuery } from "@tanstack/react-query";
import { accountingService, type JournalEntry } from "@/services/accountingService";
import { Banknote, Clock, CheckCircle2, ArrowUpRight, ArrowDownRight, Printer, LogOut, Receipt } from "lucide-react";

// ── helpers ────────────────────────────────────────────────────────────────

function getTellerName(): string {
  try {
    const raw = localStorage.getItem("lms_user");
    if (raw) {
      const parsed = JSON.parse(raw) as { username?: string; role?: string };
      return parsed.username ?? "Current Teller";
    }
  } catch {
    // ignore parse errors
  }
  return "Current Teller";
}

function formatKES(amount: number): string {
  return new Intl.NumberFormat("en-KE", {
    style: "currency",
    currency: "KES",
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(amount);
}

function formatTime(dateStr: string): string {
  try {
    return new Date(dateStr).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
  } catch {
    return dateStr;
  }
}

function classifyEntry(reference: string): string {
  if (reference?.startsWith("RPMT-")) return "Repayment Received";
  if (reference?.startsWith("DISB-")) return "Disbursement";
  return "Journal Entry";
}

function entryIcon(reference: string) {
  if (reference?.startsWith("RPMT-")) {
    return <ArrowDownRight className="h-4 w-4 text-green-500 inline mr-1" />;
  }
  if (reference?.startsWith("DISB-")) {
    return <ArrowUpRight className="h-4 w-4 text-red-500 inline mr-1" />;
  }
  return <Receipt className="h-4 w-4 text-muted-foreground inline mr-1" />;
}

function statusVariant(status: string): "default" | "secondary" | "destructive" | "outline" {
  switch (status?.toUpperCase()) {
    case "POSTED": return "default";
    case "PENDING": return "secondary";
    case "VOID":
    case "REVERSED": return "destructive";
    default: return "outline";
  }
}

// ── component ─────────────────────────────────────────────────────────────

const SESSION_OPEN_AT = new Date().toLocaleString();

const TellerSessionPage = () => {
  const tellerName = getTellerName();

  const { data: journalPage, isLoading, isError } = useQuery({
    queryKey: ["journal-entries", 0, 20],
    queryFn: () => accountingService.listJournalEntries(0, 20),
  });

  const entries: JournalEntry[] = journalPage?.content ?? [];
  const transactionCount = journalPage?.totalElements ?? 0;

  return (
    <DashboardLayout title="Teller Session" subtitle="Cash management & transactions">
      <div className="space-y-6">

        {/* ── Session Panel ─────────────────────────────────────── */}
        <Card>
          <CardHeader className="pb-3">
            <div className="flex items-center justify-between">
              <CardTitle className="flex items-center gap-2">
                <Clock className="h-5 w-5 text-primary" />
                Current Session
              </CardTitle>
              <Badge className="bg-green-500 hover:bg-green-600 text-white gap-1">
                <CheckCircle2 className="h-3.5 w-3.5" />
                Session Open
              </Badge>
            </div>
          </CardHeader>
          <CardContent>
            <div className="grid grid-cols-2 md:grid-cols-4 gap-4 text-sm">
              <div>
                <p className="text-muted-foreground">Opened At</p>
                <p className="font-medium mt-0.5">{SESSION_OPEN_AT}</p>
              </div>
              <div>
                <p className="text-muted-foreground">Teller</p>
                <p className="font-medium mt-0.5">{tellerName}</p>
              </div>
              <div>
                <p className="text-muted-foreground">Shift</p>
                <p className="font-medium mt-0.5">Morning Shift</p>
              </div>
              <div>
                <p className="text-muted-foreground">Location</p>
                <p className="font-medium mt-0.5">Head Office</p>
              </div>
            </div>
          </CardContent>
        </Card>

        {/* ── Cash Summary ──────────────────────────────────────── */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          <Card>
            <CardHeader className="pb-2">
              <CardTitle className="text-sm font-medium text-muted-foreground flex items-center gap-2">
                <Banknote className="h-4 w-4" />
                Opening Cash Balance
              </CardTitle>
            </CardHeader>
            <CardContent>
              <p className="text-2xl font-bold">{formatKES(500_000)}</p>
              <p className="text-xs text-muted-foreground mt-1">Start of shift</p>
            </CardContent>
          </Card>

          <Card>
            <CardHeader className="pb-2">
              <CardTitle className="text-sm font-medium text-muted-foreground flex items-center gap-2">
                <Receipt className="h-4 w-4" />
                Transactions Today
              </CardTitle>
            </CardHeader>
            <CardContent>
              {isLoading ? (
                <p className="text-2xl font-bold text-muted-foreground">—</p>
              ) : (
                <p className="text-2xl font-bold">{transactionCount.toLocaleString()}</p>
              )}
              <p className="text-xs text-muted-foreground mt-1">Accounting journal entries</p>
            </CardContent>
          </Card>

          <Card>
            <CardHeader className="pb-2">
              <CardTitle className="text-sm font-medium text-muted-foreground flex items-center gap-2">
                <CheckCircle2 className="h-4 w-4" />
                Closing Balance
              </CardTitle>
            </CardHeader>
            <CardContent>
              <p className="text-2xl font-bold">{formatKES(500_000)}</p>
              <p className="text-xs text-muted-foreground mt-1">Pending end of day</p>
            </CardContent>
          </Card>
        </div>

        {/* ── Quick Actions ─────────────────────────────────────── */}
        <div className="flex flex-wrap gap-3">
          <Button variant="secondary" className="gap-2">
            <Receipt className="h-4 w-4" />
            Record Cash Repayment
          </Button>
          <Button variant="outline" className="gap-2">
            <Printer className="h-4 w-4" />
            Print Receipt
          </Button>
          <Button variant="outline" className="gap-2 border-destructive text-destructive hover:bg-destructive hover:text-destructive-foreground">
            <LogOut className="h-4 w-4" />
            Close Session
          </Button>
        </div>

        <Separator />

        {/* ── Recent Transactions ───────────────────────────────── */}
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-base">
              <Banknote className="h-4 w-4 text-primary" />
              Recent Transactions
              <span className="ml-auto text-xs font-normal text-muted-foreground">Last 20 journal entries</span>
            </CardTitle>
          </CardHeader>
          <CardContent className="p-0">
            {isLoading && (
              <div className="flex items-center justify-center h-32 text-muted-foreground text-sm">
                Loading transactions…
              </div>
            )}
            {isError && (
              <div className="flex items-center justify-center h-32 text-destructive text-sm">
                Failed to load transactions. Check accounting service.
              </div>
            )}
            {!isLoading && !isError && entries.length === 0 && (
              <div className="flex items-center justify-center h-32 text-muted-foreground text-sm">
                No transactions recorded yet.
              </div>
            )}
            {!isLoading && !isError && entries.length > 0 && (
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Time</TableHead>
                    <TableHead>Type</TableHead>
                    <TableHead>Reference</TableHead>
                    <TableHead className="text-right">Amount</TableHead>
                    <TableHead className="text-center">Status</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {entries.map((entry) => (
                    <TableRow key={entry.id}>
                      <TableCell className="text-muted-foreground text-sm whitespace-nowrap">
                        {formatTime(entry.entryDate)}
                      </TableCell>
                      <TableCell className="text-sm">
                        {entryIcon(entry.reference)}
                        {classifyEntry(entry.reference)}
                      </TableCell>
                      <TableCell className="font-mono text-xs text-muted-foreground">
                        {entry.reference}
                      </TableCell>
                      <TableCell className="text-right font-medium tabular-nums">
                        {formatKES(entry.totalDebit)}
                      </TableCell>
                      <TableCell className="text-center">
                        <Badge variant={statusVariant(entry.status)} className="text-xs">
                          {entry.status}
                        </Badge>
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

export default TellerSessionPage;
