import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { DashboardLayout } from "@/components/DashboardLayout";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from "@/components/ui/table";
import { Loader2, ArrowUpCircle, ArrowDownCircle, Landmark } from "lucide-react";
import { PeriodSelector } from "@/components/PeriodSelector";
import { accountingService, type CashFlowItem } from "@/services/accountingService";

const fmt = (n: number) => `KES ${n.toLocaleString("en-KE", { minimumFractionDigits: 2 })}`;

interface SectionProps {
  title: string;
  icon: React.ReactNode;
  items: CashFlowItem[];
  total: number;
}

const CashFlowSection = ({ title, icon, items, total }: SectionProps) => (
  <Card>
    <CardHeader className="pb-2 flex flex-row items-center gap-2">
      {icon}
      <CardTitle className="text-base">{title}</CardTitle>
    </CardHeader>
    <CardContent className="p-0">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Description</TableHead>
            <TableHead className="text-right">Amount (KES)</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {items.length === 0 ? (
            <TableRow>
              <TableCell colSpan={2} className="text-center text-muted-foreground text-sm py-4">
                No items for this category
              </TableCell>
            </TableRow>
          ) : (
            items.map((item, i) => (
              <TableRow key={i}>
                <TableCell className="text-sm">{item.description}</TableCell>
                <TableCell className={`text-right font-mono text-sm ${item.amount < 0 ? "text-destructive" : ""}`}>
                  {fmt(item.amount)}
                </TableCell>
              </TableRow>
            ))
          )}
          <TableRow className="bg-muted/40 font-semibold">
            <TableCell className="text-right pr-4">Total {title}</TableCell>
            <TableCell className={`text-right font-mono ${total < 0 ? "text-destructive" : ""}`}>
              {fmt(total)}
            </TableCell>
          </TableRow>
        </TableBody>
      </Table>
    </CardContent>
  </Card>
);

const CashFlowPage = () => {
  const now = new Date();
  const [year, setYear] = useState(now.getFullYear());
  const [month, setMonth] = useState(now.getMonth() + 1);

  const { data, isLoading, isError } = useQuery({
    queryKey: ["accounting", "cash-flow", year, month],
    queryFn: () => accountingService.getCashFlow(year, month),
  });

  return (
    <DashboardLayout
      title="Cash Flow Statement"
      subtitle="IAS 7 -- Cash inflows and outflows by activity"
    >
      <div className="space-y-4">
        <div className="flex items-center justify-between">
          <span className="text-sm text-muted-foreground">Period:</span>
          <PeriodSelector year={year} month={month} onYearChange={setYear} onMonthChange={setMonth} />
        </div>

        {isLoading && (
          <div className="flex items-center justify-center h-64 text-muted-foreground">
            <Loader2 className="h-6 w-6 animate-spin mr-2" />
            <span>Loading cash flow...</span>
          </div>
        )}

        {isError && (
          <div className="flex items-center justify-center h-64 text-destructive">
            <p>Failed to load cash flow data.</p>
          </div>
        )}

        {data && (
          <div className="space-y-6">
            <CashFlowSection
              title="Operating Activities"
              icon={<ArrowUpCircle className="h-5 w-5 text-blue-500" />}
              items={data.operatingItems ?? []}
              total={data.totalOperating}
            />
            <CashFlowSection
              title="Investing Activities"
              icon={<ArrowDownCircle className="h-5 w-5 text-orange-500" />}
              items={data.investingItems ?? []}
              total={data.totalInvesting}
            />
            <CashFlowSection
              title="Financing Activities"
              icon={<Landmark className="h-5 w-5 text-purple-500" />}
              items={data.financingItems ?? []}
              total={data.totalFinancing}
            />

            {/* Summary */}
            <Card>
              <CardContent className="pt-5">
                <div className="grid grid-cols-3 gap-4 text-center">
                  <div>
                    <p className="text-sm text-muted-foreground">Opening Cash</p>
                    <p className="text-lg font-bold">{fmt(data.openingCash)}</p>
                  </div>
                  <div>
                    <p className="text-sm text-muted-foreground">Net Cash Flow</p>
                    <p className={`text-lg font-bold ${data.netCashFlow < 0 ? "text-destructive" : "text-green-700"}`}>
                      {fmt(data.netCashFlow)}
                    </p>
                  </div>
                  <div>
                    <p className="text-sm text-muted-foreground">Closing Cash</p>
                    <p className="text-lg font-bold">{fmt(data.closingCash)}</p>
                  </div>
                </div>
              </CardContent>
            </Card>
          </div>
        )}
      </div>
    </DashboardLayout>
  );
};

export default CashFlowPage;
