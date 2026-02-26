import { useState } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { DashboardLayout } from "@/components/DashboardLayout";
import { Card, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { formatKES, formatKESFull } from "@/lib/format";
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from "@/components/ui/table";
import {
  Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter,
} from "@/components/ui/dialog";
import { ArrowLeft, CreditCard, FileText, Calendar, DollarSign } from "lucide-react";
import { motion } from "framer-motion";
import { useToast } from "@/hooks/use-toast";
import { useQuery } from "@tanstack/react-query";
import { loanManagementService, type Loan } from "@/services/loanManagementService";

// ─── Adapters ──────────────────────────────────────────
function adaptRealLoan(loan: Loan) {
  const dpd = loan.dpd ?? 0;
  const stage = dpd === 0 ? "current" : dpd <= 7 ? "watch" : dpd <= 90 ? "delinquent" : "npl";
  return {
    id: loan.id,
    customer: loan.customerId,
    product: loan.productId ?? "—",
    principal: loan.disbursedAmount ?? 0,
    outstanding: loan.outstandingPrincipal ?? 0,
    emi: 0,
    nextDue: loan.nextDueDate ?? "—",
    dpd,
    stage,
    rate: loan.interestRate ?? 0,
    officer: "—",
  };
}

// Generate repayment schedule for the loan
const generateSchedule = (outstanding: number, emi: number, rate: number) => {
  const monthlyEmi = emi > 0 ? emi : Math.max(outstanding / 12, 1);
  const months = Math.max(1, Math.ceil(outstanding / monthlyEmi));
  let balance = outstanding;
  const monthlyRate = rate / 12 / 100;
  return Array.from({ length: Math.min(months, 36) }, (_, i) => {
    const interest = Math.floor(balance * monthlyRate);
    const principal = Math.min(monthlyEmi - interest, balance);
    balance = Math.max(0, balance - principal);
    const isPast = i < 3;
    return {
      no: i + 1,
      date: `${["Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec", "Jan", "Feb"][i % 12]} ${i < 10 ? "2026" : "2027"}`,
      principal,
      interest,
      emi: principal + interest,
      balance,
      status: isPast ? "paid" : "upcoming",
    };
  });
};

// Generate transaction history
const generateTransactions = (_loanId: string) => [
  { id: "TXN-001", date: "Feb 24, 2026", type: "Disbursement", description: "Loan disbursed to M-Pesa", channel: "System" },
  { id: "TXN-002", date: "Feb 28, 2026", type: "Fee", description: "Processing fee deducted", channel: "System" },
  { id: "TXN-003", date: "Mar 1, 2026", type: "Repayment", description: "Monthly EMI received", channel: "M-Pesa" },
  { id: "TXN-004", date: "Mar 5, 2026", type: "Repayment", description: "Partial payment received", channel: "Bank Transfer" },
  { id: "TXN-005", date: "Apr 1, 2026", type: "Repayment", description: "Monthly EMI received", channel: "M-Pesa" },
];

const statusBadge: Record<string, string> = {
  paid: "bg-success/15 text-success border-success/30",
  overdue: "bg-destructive/15 text-destructive border-destructive/30",
  upcoming: "bg-muted text-muted-foreground border-border",
};

const stageLabel: Record<string, { label: string; cls: string }> = {
  current: { label: "Current", cls: "bg-success/15 text-success border-success/30" },
  watch: { label: "Watch", cls: "bg-warning/15 text-warning border-warning/30" },
  delinquent: { label: "Delinquent", cls: "bg-destructive/15 text-destructive border-destructive/30" },
  npl: { label: "NPL", cls: "bg-destructive/25 text-destructive border-destructive/40" },
};

const LoanDetailPage = () => {
  const { loanId } = useParams();
  const navigate = useNavigate();
  const { toast } = useToast();
  const [paymentOpen, setPaymentOpen] = useState(false);
  const [paymentAmount, setPaymentAmount] = useState("");

  const { data: rawLoan, isLoading: loanLoading } = useQuery({
    queryKey: ["loan", loanId],
    queryFn: () => loanManagementService.getLoan(loanId!),
    enabled: !!loanId,
    retry: false,
  });

  if (loanLoading) {
    return (
      <DashboardLayout
        title="Loading..."
        subtitle=""
        breadcrumbs={[{ label: "Home", href: "/" }, { label: "Active Loans", href: "/active-loans" }]}
      >
        <div className="p-8 text-center text-muted-foreground font-sans">Loading loan details...</div>
      </DashboardLayout>
    );
  }

  const loan = rawLoan ? adaptRealLoan(rawLoan) : null;

  if (!loan) {
    return (
      <DashboardLayout
        title="Loan Not Found"
        subtitle=""
        breadcrumbs={[{ label: "Home", href: "/" }, { label: "Active Loans", href: "/active-loans" }, { label: "Not Found" }]}
      >
        <Card>
          <CardContent className="p-8 text-center text-muted-foreground font-sans">
            Loan {loanId} not found.
          </CardContent>
        </Card>
      </DashboardLayout>
    );
  }

  const schedule = generateSchedule(loan.outstanding, loan.emi, loan.rate);
  const transactions = generateTransactions(loan.id);
  const st = stageLabel[loan.stage] ?? stageLabel["current"];

  const infoCards = [
    { label: "Principal", value: formatKES(loan.principal) },
    { label: "Outstanding", value: formatKES(loan.outstanding) },
    { label: "EMI", value: loan.emi > 0 ? formatKES(loan.emi) : "—" },
    { label: "Interest Rate", value: loan.rate > 0 ? `${loan.rate}% p.a.` : "—" },
    { label: "Next Due Date", value: loan.nextDue },
    { label: "DPD", value: loan.dpd.toString(), highlight: loan.dpd > 0 },
  ];

  const handlePayment = () => {
    const amt = parseFloat(paymentAmount);
    if (!amt || amt <= 0) return;
    toast({ title: "Payment Posted", description: `${formatKES(amt)} posted to ${loan.id}` });
    setPaymentOpen(false);
    setPaymentAmount("");
  };

  return (
    <DashboardLayout
      title={loan.id}
      subtitle={`${loan.customer} — ${loan.product}`}
      breadcrumbs={[{ label: "Home", href: "/" }, { label: "Active Loans", href: "/active-loans" }, { label: loan.id }]}
    >
      <div className="space-y-4">
        {/* Top bar */}
        <div className="flex items-center justify-between">
          <Button variant="ghost" size="sm" className="text-xs font-sans" onClick={() => navigate("/active-loans")}>
            <ArrowLeft className="h-3.5 w-3.5 mr-1" /> Back to Loans
          </Button>
          <div className="flex items-center gap-2">
            <Badge variant="outline" className={`text-[10px] font-sans ${st.cls}`}>{st.label}</Badge>
            <Button size="sm" className="text-xs font-sans" onClick={() => setPaymentOpen(true)}>
              <DollarSign className="h-3.5 w-3.5 mr-1" /> Post Payment
            </Button>
          </div>
        </div>

        {/* Summary cards */}
        <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-3">
          {infoCards.map((c, i) => (
            <motion.div key={c.label} initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: i * 0.04 }}>
              <Card>
                <CardContent className="p-3">
                  <p className="text-[9px] uppercase tracking-wider text-muted-foreground font-sans">{c.label}</p>
                  <p className={`text-sm font-mono font-bold mt-0.5 ${c.highlight ? "text-destructive" : ""}`}>{c.value}</p>
                </CardContent>
              </Card>
            </motion.div>
          ))}
        </div>

        {/* Tabs */}
        <Tabs defaultValue="schedule" className="w-full">
          <TabsList className="font-sans text-xs">
            <TabsTrigger value="schedule" className="text-xs"><Calendar className="h-3.5 w-3.5 mr-1" /> Schedule</TabsTrigger>
            <TabsTrigger value="transactions" className="text-xs"><CreditCard className="h-3.5 w-3.5 mr-1" /> Transactions</TabsTrigger>
            <TabsTrigger value="details" className="text-xs"><FileText className="h-3.5 w-3.5 mr-1" /> Details</TabsTrigger>
          </TabsList>

          <TabsContent value="schedule">
            <Card>
              <CardContent className="p-0">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead className="text-[10px] font-sans">#</TableHead>
                      <TableHead className="text-[10px] font-sans">Due Date</TableHead>
                      <TableHead className="text-[10px] font-sans text-right">Principal</TableHead>
                      <TableHead className="text-[10px] font-sans text-right">Interest</TableHead>
                      <TableHead className="text-[10px] font-sans text-right">EMI</TableHead>
                      <TableHead className="text-[10px] font-sans text-right">Balance</TableHead>
                      <TableHead className="text-[10px] font-sans">Status</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {schedule.map((row) => (
                      <TableRow key={row.no} className="table-row-hover">
                        <TableCell className="text-xs font-mono">{row.no}</TableCell>
                        <TableCell className="text-xs font-sans">{row.date}</TableCell>
                        <TableCell className="text-xs font-mono text-right">{formatKES(row.principal)}</TableCell>
                        <TableCell className="text-xs font-mono text-right">{formatKES(row.interest)}</TableCell>
                        <TableCell className="text-xs font-mono text-right font-semibold">{formatKES(row.emi)}</TableCell>
                        <TableCell className="text-xs font-mono text-right">{formatKES(row.balance)}</TableCell>
                        <TableCell>
                          <Badge variant="outline" className={`text-[9px] font-sans capitalize ${statusBadge[row.status]}`}>
                            {row.status}
                          </Badge>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </CardContent>
            </Card>
          </TabsContent>

          <TabsContent value="transactions">
            <Card>
              <CardContent className="p-0">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead className="text-[10px] font-sans">Txn ID</TableHead>
                      <TableHead className="text-[10px] font-sans">Date</TableHead>
                      <TableHead className="text-[10px] font-sans">Type</TableHead>
                      <TableHead className="text-[10px] font-sans">Description</TableHead>
                      <TableHead className="text-[10px] font-sans">Channel</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {transactions.map((tx) => (
                      <TableRow key={tx.id} className="table-row-hover">
                        <TableCell className="text-xs font-mono text-info">{tx.id}</TableCell>
                        <TableCell className="text-xs font-sans">{tx.date}</TableCell>
                        <TableCell><Badge variant="outline" className="text-[9px] font-sans">{tx.type}</Badge></TableCell>
                        <TableCell className="text-xs font-sans text-muted-foreground">{tx.description}</TableCell>
                        <TableCell className="text-xs font-sans">{tx.channel}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </CardContent>
            </Card>
          </TabsContent>

          <TabsContent value="details">
            <Card>
              <CardContent className="p-5 grid grid-cols-2 md:grid-cols-3 gap-4">
                {[
                  ["Loan Officer", loan.officer],
                  ["Product", loan.product],
                  ["Principal Amount", formatKES(loan.principal)],
                  ["Outstanding Balance", formatKES(loan.outstanding)],
                  ["Interest Rate", loan.rate > 0 ? `${loan.rate}% p.a.` : "—"],
                  ["Monthly EMI", loan.emi > 0 ? formatKES(loan.emi) : "—"],
                  ["Days Past Due", `${loan.dpd} days`],
                  ["Classification", st.label],
                  ["Next Due Date", loan.nextDue],
                ].map(([label, value]) => (
                  <div key={label}>
                    <p className="text-[10px] uppercase tracking-wider text-muted-foreground font-sans mb-0.5">{label}</p>
                    <p className="text-sm font-sans font-medium">{value}</p>
                  </div>
                ))}
              </CardContent>
            </Card>
          </TabsContent>
        </Tabs>

        {/* Payment Dialog */}
        <Dialog open={paymentOpen} onOpenChange={setPaymentOpen}>
          <DialogContent className="sm:max-w-md">
            <DialogHeader>
              <DialogTitle className="font-heading">Post Payment — {loan.id}</DialogTitle>
            </DialogHeader>
            <div className="space-y-3 py-2">
              <div>
                <p className="text-xs text-muted-foreground font-sans mb-1">Outstanding Balance</p>
                <p className="text-lg font-mono font-bold">{formatKES(loan.outstanding)}</p>
              </div>
              <div>
                <label className="text-xs font-sans font-medium">Payment Amount (KES)</label>
                <Input
                  type="number"
                  placeholder="Enter amount"
                  className="mt-1 font-mono"
                  value={paymentAmount}
                  onChange={(e) => setPaymentAmount(e.target.value)}
                />
              </div>
            </div>
            <DialogFooter>
              <Button variant="outline" size="sm" onClick={() => setPaymentOpen(false)} className="text-xs font-sans">Cancel</Button>
              <Button size="sm" onClick={handlePayment} className="text-xs font-sans">Confirm Payment</Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>
      </div>
    </DashboardLayout>
  );
};

export default LoanDetailPage;
