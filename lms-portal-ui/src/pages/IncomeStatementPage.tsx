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
import { Loader2, TrendingUp, TrendingDown } from "lucide-react";
import { accountingService, type TrialBalanceAccount } from "@/services/accountingService";

const fmt = (n: number) => `KES ${n.toLocaleString("en-KE", { minimumFractionDigits: 2 })}`;

interface SectionProps {
  title: string;
  icon: React.ReactNode;
  rows: TrialBalanceAccount[];
  total: number;
}

const IncomeSection = ({ title, icon, rows, total }: SectionProps) => (
  <Card>
    <CardHeader className="pb-2 flex flex-row items-center gap-2">
      {icon}
      <CardTitle className="text-base">{title}</CardTitle>
    </CardHeader>
    <CardContent className="p-0">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Code</TableHead>
            <TableHead>Account Name</TableHead>
            <TableHead className="text-right">Amount</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {rows.map((row) => (
            <TableRow key={row.accountId}>
              <TableCell className="text-muted-foreground text-sm font-mono">
                {row.accountCode}
              </TableCell>
              <TableCell>{row.accountName}</TableCell>
              <TableCell className="text-right font-medium">{fmt(row.balance)}</TableCell>
            </TableRow>
          ))}
          <TableRow className="bg-muted/40 font-semibold">
            <TableCell colSpan={2} className="text-right pr-4">
              Total {title}
            </TableCell>
            <TableCell className="text-right">{fmt(total)}</TableCell>
          </TableRow>
        </TableBody>
      </Table>
    </CardContent>
  </Card>
);

const IncomeStatementPage = () => {
  const { data, isLoading, isError } = useQuery({
    queryKey: ["accounting", "trial-balance"],
    queryFn: () => accountingService.getTrialBalance(),
  });

  const accounts = data?.accounts ?? [];

  const income = accounts.filter((l) => l.accountType === "INCOME");
  const expenses = accounts.filter((l) => l.accountType === "EXPENSE");

  const totalIncome = income.reduce((s, r) => s + r.balance, 0);
  const totalExpenses = expenses.reduce((s, r) => s + r.balance, 0);
  const netIncome = totalIncome - totalExpenses;

  return (
    <DashboardLayout
      title="Income Statement"
      subtitle="Revenue and expenses for the current period"
    >
      {isLoading && (
        <div className="flex items-center justify-center h-64 text-muted-foreground">
          <Loader2 className="h-6 w-6 animate-spin mr-2" />
          <span>Loading income statement...</span>
        </div>
      )}

      {isError && (
        <div className="flex items-center justify-center h-64 text-destructive">
          <p>Failed to load income data. Please try again.</p>
        </div>
      )}

      {data && (
        <div className="space-y-6">
          <IncomeSection
            title="Income"
            icon={<TrendingUp className="h-5 w-5 text-green-600" />}
            rows={income}
            total={totalIncome}
          />
          <IncomeSection
            title="Expenses"
            icon={<TrendingDown className="h-5 w-5 text-red-500" />}
            rows={expenses}
            total={totalExpenses}
          />

          {/* Net Income summary */}
          <Card className={netIncome >= 0 ? "border-green-200" : "border-red-200"}>
            <CardContent className="pt-5">
              <div className="flex items-center justify-between">
                <div>
                  <p className="text-sm text-muted-foreground">Net Income</p>
                  <p className="text-sm text-muted-foreground">
                    Total Income − Total Expenses
                  </p>
                </div>
                <div className="flex items-center gap-3">
                  <p className={`text-2xl font-bold ${netIncome >= 0 ? "text-green-700" : "text-red-600"}`}>
                    {fmt(netIncome)}
                  </p>
                  <Badge
                    variant={netIncome >= 0 ? "default" : "destructive"}
                    className={netIncome >= 0 ? "bg-green-600" : undefined}
                  >
                    {netIncome >= 0 ? "Profit" : "Loss"}
                  </Badge>
                </div>
              </div>
            </CardContent>
          </Card>
        </div>
      )}
    </DashboardLayout>
  );
};

export default IncomeStatementPage;
