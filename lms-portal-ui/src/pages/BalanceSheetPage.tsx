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
import { Loader2, CheckCircle2, AlertCircle } from "lucide-react";
import { accountingService, type TrialBalanceAccount } from "@/services/accountingService";

const fmt = (n: number) => `KES ${n.toLocaleString("en-KE", { minimumFractionDigits: 2 })}`;

interface SectionProps {
  title: string;
  rows: TrialBalanceAccount[];
  total: number;
}

const AccountSection = ({ title, rows, total }: SectionProps) => (
  <Card>
    <CardHeader className="pb-2">
      <CardTitle className="text-base">{title}</CardTitle>
    </CardHeader>
    <CardContent className="p-0">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Code</TableHead>
            <TableHead>Account Name</TableHead>
            <TableHead className="text-right">Balance</TableHead>
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

const BalanceSheetPage = () => {
  const { data, isLoading, isError } = useQuery({
    queryKey: ["accounting", "trial-balance"],
    queryFn: () => accountingService.getTrialBalance(),
  });

  const accounts = data?.accounts ?? [];

  const assets = accounts.filter((l) => l.accountType === "ASSET");
  const liabilities = accounts.filter((l) => l.accountType === "LIABILITY");
  const equity = accounts.filter((l) => l.accountType === "EQUITY");

  const totalAssets = assets.reduce((s, r) => s + r.balance, 0);
  const totalLiabilities = liabilities.reduce((s, r) => s + r.balance, 0);
  const totalEquity = equity.reduce((s, r) => s + r.balance, 0);
  const liabEquity = totalLiabilities + totalEquity;
  const balanced = data?.balanced ?? Math.abs(totalAssets - liabEquity) < 0.01;

  return (
    <DashboardLayout
      title="Balance Sheet"
      subtitle="Assets, liabilities, and equity as of today"
    >
      {isLoading && (
        <div className="flex items-center justify-center h-64 text-muted-foreground">
          <Loader2 className="h-6 w-6 animate-spin mr-2" />
          <span>Loading balance sheet...</span>
        </div>
      )}

      {isError && (
        <div className="flex items-center justify-center h-64 text-destructive">
          <p>Failed to load trial balance. Please try again.</p>
        </div>
      )}

      {data && (
        <div className="space-y-6">
          <AccountSection title="Assets" rows={assets} total={totalAssets} />
          <AccountSection title="Liabilities" rows={liabilities} total={totalLiabilities} />
          <AccountSection title="Equity" rows={equity} total={totalEquity} />

          {/* Balance check summary */}
          <Card>
            <CardContent className="pt-5">
              <div className="flex items-center justify-between">
                <div className="space-y-1">
                  <p className="text-sm text-muted-foreground">Total Assets</p>
                  <p className="text-xl font-bold">{fmt(totalAssets)}</p>
                </div>
                <div className="text-muted-foreground text-lg font-semibold">=</div>
                <div className="space-y-1 text-right">
                  <p className="text-sm text-muted-foreground">Liabilities + Equity</p>
                  <p className="text-xl font-bold">{fmt(liabEquity)}</p>
                </div>
                <div className="flex items-center gap-2">
                  {balanced ? (
                    <>
                      <CheckCircle2 className="h-5 w-5 text-green-600" />
                      <Badge variant="default" className="bg-green-600">Balanced</Badge>
                    </>
                  ) : (
                    <>
                      <AlertCircle className="h-5 w-5 text-destructive" />
                      <Badge variant="destructive">Out of Balance</Badge>
                    </>
                  )}
                </div>
              </div>
            </CardContent>
          </Card>
        </div>
      )}
    </DashboardLayout>
  );
};

export default BalanceSheetPage;
