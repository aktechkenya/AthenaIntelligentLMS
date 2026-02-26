import { DashboardLayout } from "@/components/DashboardLayout";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Badge } from "@/components/ui/badge";
import { Input } from "@/components/ui/input";
import { Search } from "lucide-react";

const transactions = [
  { id: "TXN-98421", date: "Feb 24, 14:32", type: "Disbursement", from: "Pool Account", to: "GreenTech Solutions", amount: "$250,000.00", status: "Completed" },
  { id: "TXN-98420", date: "Feb 24, 13:15", type: "Repayment", from: "Maria Fernandez", to: "Loan LN-04518", amount: "$400.00", status: "Completed" },
  { id: "TXN-98419", date: "Feb 24, 11:45", type: "Fee", from: "Peter Ochieng", to: "Fee Income", amount: "$25.00", status: "Completed" },
  { id: "TXN-98418", date: "Feb 24, 10:22", type: "Transfer", from: "Sarah Kimani", to: "External Bank", amount: "$2,000.00", status: "Pending" },
  { id: "TXN-98417", date: "Feb 24, 09:10", type: "Repayment", from: "Acme Industries", to: "Loan LN-04510", amount: "$8,500.00", status: "Completed" },
  { id: "TXN-98416", date: "Feb 23, 23:59", type: "Interest Accrual", from: "System", to: "Multiple Accounts", amount: "$12,450.00", status: "Completed" },
];

const typeColors: Record<string, string> = {
  Disbursement: "bg-info/15 text-info border-info/30",
  Repayment: "bg-success/15 text-success border-success/30",
  Fee: "bg-accent/15 text-accent-foreground border-accent/30",
  Transfer: "bg-primary/10 text-primary border-primary/20",
  "Interest Accrual": "bg-muted text-muted-foreground",
};

const TransactionsPage = () => (
  <DashboardLayout title="Transactions" subtitle="Real-time transaction processing & history">
    <div className="space-y-4 animate-fade-in">
      <div className="relative w-full sm:w-64">
        <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-muted-foreground" />
        <Input placeholder="Search transactions..." className="pl-8 h-9 text-xs" />
      </div>
      <Card>
        <CardContent className="p-0">
          <Table>
            <TableHeader>
              <TableRow className="hover:bg-transparent">
                <TableHead className="text-[10px] uppercase tracking-wider">TXN ID</TableHead>
                <TableHead className="text-[10px] uppercase tracking-wider">Date</TableHead>
                <TableHead className="text-[10px] uppercase tracking-wider">Type</TableHead>
                <TableHead className="text-[10px] uppercase tracking-wider">From</TableHead>
                <TableHead className="text-[10px] uppercase tracking-wider">To</TableHead>
                <TableHead className="text-[10px] uppercase tracking-wider text-right">Amount</TableHead>
                <TableHead className="text-[10px] uppercase tracking-wider">Status</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {transactions.map((txn) => (
                <TableRow key={txn.id} className="table-row-hover cursor-pointer">
                  <TableCell className="text-xs font-mono font-medium">{txn.id}</TableCell>
                  <TableCell className="text-xs text-muted-foreground">{txn.date}</TableCell>
                  <TableCell>
                    <Badge variant="outline" className={`text-[10px] font-semibold ${typeColors[txn.type] || ""}`}>
                      {txn.type}
                    </Badge>
                  </TableCell>
                  <TableCell className="text-xs">{txn.from}</TableCell>
                  <TableCell className="text-xs">{txn.to}</TableCell>
                  <TableCell className="text-xs font-medium text-right">{txn.amount}</TableCell>
                  <TableCell>
                    <Badge variant="outline" className={`text-[10px] font-semibold ${txn.status === "Completed" ? "bg-success/15 text-success border-success/30" : "bg-warning/15 text-warning border-warning/30"}`}>
                      {txn.status}
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

export default TransactionsPage;
