import { useState, useEffect, useCallback, useMemo } from "react";
import { DashboardLayout } from "@/components/DashboardLayout";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Separator } from "@/components/ui/separator";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { useQuery, useMutation } from "@tanstack/react-query";
import { useToast } from "@/hooks/use-toast";
import { accountingService, type JournalEntry } from "@/services/accountingService";
import { apiPost } from "@/lib/api";
import {
  Banknote,
  Clock,
  CheckCircle2,
  ArrowUpRight,
  ArrowDownRight,
  Printer,
  LogOut,
  Receipt,
  PlayCircle,
  XCircle,
  Timer,
} from "lucide-react";

// ── constants ──────────────────────────────────────────────────────────────

const SESSION_STORAGE_KEY = "lms_teller_session";

interface TellerSession {
  openedAt: string; // ISO date string
  tellerName: string;
}

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

function getSession(): TellerSession | null {
  try {
    const raw = localStorage.getItem(SESSION_STORAGE_KEY);
    if (raw) return JSON.parse(raw) as TellerSession;
  } catch {
    // ignore
  }
  return null;
}

function saveSession(session: TellerSession | null) {
  if (session) {
    localStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(session));
  } else {
    localStorage.removeItem(SESSION_STORAGE_KEY);
  }
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

function formatDuration(ms: number): string {
  const totalSeconds = Math.floor(ms / 1000);
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;
  return `${String(hours).padStart(2, "0")}:${String(minutes).padStart(2, "0")}:${String(seconds).padStart(2, "0")}`;
}

function generateReference(): string {
  const ts = Date.now().toString(36).toUpperCase();
  const rand = Math.random().toString(36).substring(2, 6).toUpperCase();
  return `RPMT-CASH-${ts}-${rand}`;
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

function isToday(dateStr: string): boolean {
  try {
    const d = new Date(dateStr);
    const now = new Date();
    return d.toDateString() === now.toDateString();
  } catch {
    return false;
  }
}

// ── component ─────────────────────────────────────────────────────────────

const TellerSessionPage = () => {
  const tellerName = getTellerName();
  const { toast } = useToast();

  // Session state
  const [session, setSession] = useState<TellerSession | null>(getSession);
  const [elapsed, setElapsed] = useState("");
  const [showCloseDialog, setShowCloseDialog] = useState(false);

  // Repayment dialog state
  const [showRepaymentDialog, setShowRepaymentDialog] = useState(false);
  const [repaymentLoanId, setRepaymentLoanId] = useState("");
  const [repaymentAmount, setRepaymentAmount] = useState("");

  // Timer
  useEffect(() => {
    if (!session) {
      setElapsed("");
      return;
    }
    const tick = () => {
      const ms = Date.now() - new Date(session.openedAt).getTime();
      setElapsed(formatDuration(ms));
    };
    tick();
    const interval = setInterval(tick, 1000);
    return () => clearInterval(interval);
  }, [session]);

  // Data
  const { data: journalPage, isLoading, isError } = useQuery({
    queryKey: ["journal-entries", 0, 50],
    queryFn: () => accountingService.listJournalEntries(0, 50),
  });

  const entries: JournalEntry[] = journalPage?.content ?? [];

  // Cash summary calculations
  const cashSummary = useMemo(() => {
    const todayEntries = entries.filter((e) => isToday(e.entryDate));
    let cashIn = 0;
    let cashOut = 0;
    let openingBalance = 0;

    if (todayEntries.length > 0) {
      // First transaction amount as opening context
      const firstEntry = todayEntries[todayEntries.length - 1];
      openingBalance = firstEntry?.totalDebit ?? 0;
    }

    for (const entry of todayEntries) {
      if (entry.reference?.startsWith("RPMT-")) {
        cashIn += entry.totalDebit;
      } else if (entry.reference?.startsWith("DISB-")) {
        cashOut += entry.totalDebit;
      }
    }

    const baseBalance = 500_000; // Configurable opening cash float
    const runningBalance = baseBalance + cashIn - cashOut;

    return { openingBalance: baseBalance, cashIn, cashOut, runningBalance, todayCount: todayEntries.length };
  }, [entries]);

  // Session management
  const openSession = useCallback(() => {
    const newSession: TellerSession = {
      openedAt: new Date().toISOString(),
      tellerName,
    };
    saveSession(newSession);
    setSession(newSession);
    toast({ title: "Session Opened", description: `Teller session started for ${tellerName}` });
  }, [tellerName, toast]);

  const closeSession = useCallback(() => {
    saveSession(null);
    setSession(null);
    setShowCloseDialog(false);
    toast({ title: "Session Closed", description: "Teller session ended successfully" });
  }, [toast]);

  // Cash repayment mutation
  const repaymentMutation = useMutation({
    mutationFn: async (payload: { loanId: string; amount: number; paymentMethod: string; reference: string }) => {
      const result = await apiPost<{ id: string }>("/proxy/loans/api/v1/repayments", payload);
      if (result.error) throw new Error(result.error);
      return result.data;
    },
    onSuccess: (_data, variables) => {
      toast({
        title: "Cash Repayment Recorded",
        description: `${formatKES(variables.amount)} received for loan ${variables.loanId}`,
      });
      setShowRepaymentDialog(false);
      setRepaymentLoanId("");
      setRepaymentAmount("");
    },
    onError: (err: Error) => {
      toast({ title: "Repayment Failed", description: err.message, variant: "destructive" });
    },
  });

  const handleSubmitRepayment = () => {
    const amount = parseFloat(repaymentAmount);
    if (!repaymentLoanId.trim()) {
      toast({ title: "Validation Error", description: "Loan ID is required", variant: "destructive" });
      return;
    }
    if (isNaN(amount) || amount <= 0) {
      toast({ title: "Validation Error", description: "Enter a valid amount greater than 0", variant: "destructive" });
      return;
    }
    repaymentMutation.mutate({
      loanId: repaymentLoanId.trim(),
      amount,
      paymentMethod: "CASH",
      reference: generateReference(),
    });
  };

  const isSessionOpen = session !== null;
  const transactionCount = cashSummary.todayCount;

  return (
    <DashboardLayout title="Teller Session" subtitle="Cash management & transactions">
      <div className="space-y-6">

        {/* Session Panel */}
        <Card>
          <CardHeader className="pb-3">
            <div className="flex items-center justify-between">
              <CardTitle className="flex items-center gap-2">
                <Clock className="h-5 w-5 text-primary" />
                Current Session
              </CardTitle>
              {isSessionOpen ? (
                <Badge className="bg-green-500 hover:bg-green-600 text-white gap-1">
                  <CheckCircle2 className="h-3.5 w-3.5" />
                  Session Open
                </Badge>
              ) : (
                <Badge variant="secondary" className="gap-1">
                  <XCircle className="h-3.5 w-3.5" />
                  Session Closed
                </Badge>
              )}
            </div>
          </CardHeader>
          <CardContent>
            {isSessionOpen && session ? (
              <div className="grid grid-cols-2 md:grid-cols-5 gap-4 text-sm">
                <div>
                  <p className="text-muted-foreground">Opened At</p>
                  <p className="font-medium mt-0.5">{new Date(session.openedAt).toLocaleString()}</p>
                </div>
                <div>
                  <p className="text-muted-foreground">Teller</p>
                  <p className="font-medium mt-0.5">{session.tellerName}</p>
                </div>
                <div>
                  <p className="text-muted-foreground">Shift</p>
                  <p className="font-medium mt-0.5">Morning Shift</p>
                </div>
                <div>
                  <p className="text-muted-foreground">Location</p>
                  <p className="font-medium mt-0.5">Head Office</p>
                </div>
                <div>
                  <p className="text-muted-foreground">Duration</p>
                  <p className="font-medium mt-0.5 flex items-center gap-1">
                    <Timer className="h-3.5 w-3.5 text-primary" />
                    {elapsed}
                  </p>
                </div>
              </div>
            ) : (
              <div className="text-center py-4 text-muted-foreground text-sm">
                No active session. Open a session to begin processing transactions.
              </div>
            )}
          </CardContent>
        </Card>

        {/* Cash Summary */}
        <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
          <Card>
            <CardHeader className="pb-2">
              <CardTitle className="text-sm font-medium text-muted-foreground flex items-center gap-2">
                <Banknote className="h-4 w-4" />
                Opening Balance
              </CardTitle>
            </CardHeader>
            <CardContent>
              <p className="text-2xl font-bold">{formatKES(cashSummary.openingBalance)}</p>
              <p className="text-xs text-muted-foreground mt-1">Start of shift float</p>
            </CardContent>
          </Card>

          <Card>
            <CardHeader className="pb-2">
              <CardTitle className="text-sm font-medium text-muted-foreground flex items-center gap-2">
                <ArrowDownRight className="h-4 w-4 text-green-500" />
                Cash In (Repayments)
              </CardTitle>
            </CardHeader>
            <CardContent>
              <p className="text-2xl font-bold text-green-600">{formatKES(cashSummary.cashIn)}</p>
              <p className="text-xs text-muted-foreground mt-1">Received today</p>
            </CardContent>
          </Card>

          <Card>
            <CardHeader className="pb-2">
              <CardTitle className="text-sm font-medium text-muted-foreground flex items-center gap-2">
                <ArrowUpRight className="h-4 w-4 text-red-500" />
                Cash Out (Disbursements)
              </CardTitle>
            </CardHeader>
            <CardContent>
              <p className="text-2xl font-bold text-red-600">{formatKES(cashSummary.cashOut)}</p>
              <p className="text-xs text-muted-foreground mt-1">Disbursed today</p>
            </CardContent>
          </Card>

          <Card>
            <CardHeader className="pb-2">
              <CardTitle className="text-sm font-medium text-muted-foreground flex items-center gap-2">
                <CheckCircle2 className="h-4 w-4" />
                Running Balance
              </CardTitle>
            </CardHeader>
            <CardContent>
              <p className="text-2xl font-bold">{formatKES(cashSummary.runningBalance)}</p>
              <p className="text-xs text-muted-foreground mt-1">{transactionCount} transactions today</p>
            </CardContent>
          </Card>
        </div>

        {/* Quick Actions */}
        <div className="flex flex-wrap gap-3">
          {!isSessionOpen ? (
            <Button variant="default" className="gap-2" onClick={openSession}>
              <PlayCircle className="h-4 w-4" />
              Open Session
            </Button>
          ) : (
            <>
              <Button
                variant="secondary"
                className="gap-2"
                onClick={() => setShowRepaymentDialog(true)}
              >
                <Receipt className="h-4 w-4" />
                Record Cash Repayment
              </Button>
              <Button
                variant="outline"
                className="gap-2"
                onClick={() => window.print()}
              >
                <Printer className="h-4 w-4" />
                Print Receipt
              </Button>
              <Button
                variant="outline"
                className="gap-2 border-destructive text-destructive hover:bg-destructive hover:text-destructive-foreground"
                onClick={() => setShowCloseDialog(true)}
              >
                <LogOut className="h-4 w-4" />
                Close Session
              </Button>
            </>
          )}
        </div>

        <Separator />

        {/* Recent Transactions */}
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-base">
              <Banknote className="h-4 w-4 text-primary" />
              Recent Transactions
              <span className="ml-auto text-xs font-normal text-muted-foreground">Last 50 journal entries</span>
            </CardTitle>
          </CardHeader>
          <CardContent className="p-0">
            {isLoading && (
              <div className="flex items-center justify-center h-32 text-muted-foreground text-sm">
                Loading transactions...
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

        {/* Close Session Confirmation Dialog */}
        <Dialog open={showCloseDialog} onOpenChange={setShowCloseDialog}>
          <DialogContent>
            <DialogHeader>
              <DialogTitle>Close Teller Session</DialogTitle>
              <DialogDescription>
                Review the session summary before closing.
              </DialogDescription>
            </DialogHeader>
            <div className="space-y-3 py-2">
              <div className="grid grid-cols-2 gap-3 text-sm">
                <div className="p-3 rounded-lg bg-muted/50">
                  <p className="text-muted-foreground text-xs">Session Duration</p>
                  <p className="font-semibold">{elapsed}</p>
                </div>
                <div className="p-3 rounded-lg bg-muted/50">
                  <p className="text-muted-foreground text-xs">Transactions Today</p>
                  <p className="font-semibold">{transactionCount}</p>
                </div>
                <div className="p-3 rounded-lg bg-muted/50">
                  <p className="text-muted-foreground text-xs">Cash In</p>
                  <p className="font-semibold text-green-600">{formatKES(cashSummary.cashIn)}</p>
                </div>
                <div className="p-3 rounded-lg bg-muted/50">
                  <p className="text-muted-foreground text-xs">Cash Out</p>
                  <p className="font-semibold text-red-600">{formatKES(cashSummary.cashOut)}</p>
                </div>
              </div>
              <div className="p-3 rounded-lg border">
                <p className="text-muted-foreground text-xs">Closing Balance</p>
                <p className="font-bold text-lg">{formatKES(cashSummary.runningBalance)}</p>
              </div>
            </div>
            <DialogFooter>
              <Button variant="outline" onClick={() => setShowCloseDialog(false)}>
                Cancel
              </Button>
              <Button variant="destructive" onClick={closeSession}>
                Confirm & Close Session
              </Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>

        {/* Cash Repayment Dialog */}
        <Dialog open={showRepaymentDialog} onOpenChange={setShowRepaymentDialog}>
          <DialogContent>
            <DialogHeader>
              <DialogTitle>Record Cash Repayment</DialogTitle>
              <DialogDescription>
                Enter the loan repayment details. A reference will be auto-generated.
              </DialogDescription>
            </DialogHeader>
            <div className="space-y-4 py-2">
              <div className="space-y-2">
                <Label htmlFor="loan-id">Loan ID</Label>
                <Input
                  id="loan-id"
                  placeholder="e.g. LN-00001"
                  value={repaymentLoanId}
                  onChange={(e) => setRepaymentLoanId(e.target.value)}
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="repayment-amount">Amount (KES)</Label>
                <Input
                  id="repayment-amount"
                  type="number"
                  placeholder="0.00"
                  min="0"
                  step="0.01"
                  value={repaymentAmount}
                  onChange={(e) => setRepaymentAmount(e.target.value)}
                />
              </div>
              <div className="space-y-2">
                <Label>Reference</Label>
                <p className="text-sm font-mono text-muted-foreground bg-muted/50 p-2 rounded">
                  Auto-generated on submit
                </p>
              </div>
              <div className="space-y-2">
                <Label>Payment Method</Label>
                <p className="text-sm font-medium">CASH</p>
              </div>
            </div>
            <DialogFooter>
              <Button variant="outline" onClick={() => setShowRepaymentDialog(false)}>
                Cancel
              </Button>
              <Button
                onClick={handleSubmitRepayment}
                disabled={repaymentMutation.isPending}
              >
                {repaymentMutation.isPending ? "Recording..." : "Record Repayment"}
              </Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>

      </div>
    </DashboardLayout>
  );
};

export default TellerSessionPage;
