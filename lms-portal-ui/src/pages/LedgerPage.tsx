import { useState } from "react";
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
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from "@/components/ui/select";
import { Loader2, BookOpen } from "lucide-react";
import { accountingService, type JournalEntry } from "@/services/accountingService";

const fmt = (n: number) =>
  n > 0 ? n.toLocaleString("en-KE", { minimumFractionDigits: 2 }) : "\u2014";

const refBadgeVariant = (ref: string): "default" | "secondary" | "outline" => {
  if (ref.startsWith("RPMT-")) return "secondary";
  if (ref.startsWith("DISB-")) return "default";
  return "outline";
};

const refLabel = (ref: string) => {
  if (ref.startsWith("RPMT-")) return "Repayment";
  if (ref.startsWith("DISB-")) return "Disbursement";
  return "Other";
};

const statusVariant = (status: string): "default" | "secondary" | "outline" => {
  if (status === "POSTED") return "default";
  if (status === "PENDING") return "secondary";
  return "outline";
};

const LedgerPage = () => {
  const [fromDate, setFromDate] = useState("");
  const [toDate, setToDate] = useState("");
  const [statusFilter, setStatusFilter] = useState("ALL");

  const { data: page, isLoading, isError } = useQuery({
    queryKey: ["accounting", "journal-entries", fromDate, toDate, statusFilter],
    queryFn: () =>
      accountingService.listJournalEntries(
        0,
        100,
        fromDate || undefined,
        toDate || undefined,
        statusFilter !== "ALL" ? statusFilter : undefined,
      ),
  });

  const entries: JournalEntry[] = page?.content ?? [];

  return (
    <DashboardLayout
      title="General Ledger"
      subtitle="Journal entries across all GL accounts"
    >
      <div className="space-y-4">
        <div className="flex items-center gap-3 flex-wrap">
          <div className="flex items-center gap-1.5">
            <label className="text-xs text-muted-foreground">From:</label>
            <input type="date" value={fromDate} onChange={(e) => setFromDate(e.target.value)}
              className="h-8 px-2 text-xs border rounded-md bg-background" />
          </div>
          <div className="flex items-center gap-1.5">
            <label className="text-xs text-muted-foreground">To:</label>
            <input type="date" value={toDate} onChange={(e) => setToDate(e.target.value)}
              className="h-8 px-2 text-xs border rounded-md bg-background" />
          </div>
          <Select value={statusFilter} onValueChange={setStatusFilter}>
            <SelectTrigger className="w-[150px] h-8 text-xs">
              <SelectValue placeholder="All statuses" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="ALL" className="text-xs">All statuses</SelectItem>
              <SelectItem value="DRAFT" className="text-xs">Draft</SelectItem>
              <SelectItem value="PENDING_APPROVAL" className="text-xs">Pending Approval</SelectItem>
              <SelectItem value="POSTED" className="text-xs">Posted</SelectItem>
              <SelectItem value="REJECTED" className="text-xs">Rejected</SelectItem>
              <SelectItem value="REVERSED" className="text-xs">Reversed</SelectItem>
            </SelectContent>
          </Select>
        </div>

        {isLoading && (
          <div className="flex items-center justify-center h-64 text-muted-foreground">
            <Loader2 className="h-6 w-6 animate-spin mr-2" />
            <span>Loading journal entries...</span>
          </div>
        )}

        {isError && (
          <div className="flex items-center justify-center h-64 text-destructive">
            <p>Failed to load journal entries. Please try again.</p>
          </div>
        )}

        {!isLoading && !isError && (
          <Card>
            <CardHeader className="flex flex-row items-center gap-2">
              <BookOpen className="h-5 w-5 text-muted-foreground" />
              <CardTitle className="text-base">
                Journal Entries
                {entries.length > 0 && (
                  <span className="ml-2 text-sm font-normal text-muted-foreground">
                    ({entries.length} records)
                  </span>
                )}
              </CardTitle>
            </CardHeader>
            <CardContent className="p-0">
              {entries.length === 0 ? (
                <div className="flex items-center justify-center h-32 text-muted-foreground text-sm">
                  No journal entries found.
                </div>
              ) : (
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Reference</TableHead>
                      <TableHead>Description</TableHead>
                      <TableHead>Date</TableHead>
                      <TableHead className="text-right">Debit (KES)</TableHead>
                      <TableHead className="text-right">Credit (KES)</TableHead>
                      <TableHead>Status</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {entries.map((entry) => (
                      <TableRow key={entry.id}>
                        <TableCell>
                          <div className="flex items-center gap-2">
                            <Badge variant={refBadgeVariant(entry.reference)} className="text-xs font-mono">
                              {refLabel(entry.reference)}
                            </Badge>
                            <span className="text-xs font-mono text-muted-foreground">
                              {entry.reference}
                            </span>
                          </div>
                        </TableCell>
                        <TableCell className="max-w-[240px] truncate text-sm">
                          {entry.description}
                        </TableCell>
                        <TableCell className="text-sm text-muted-foreground whitespace-nowrap">
                          {entry.entryDate ? new Date(entry.entryDate).toLocaleDateString("en-KE") : "\u2014"}
                        </TableCell>
                        <TableCell className="text-right font-mono text-sm">
                          {fmt(entry.totalDebit)}
                        </TableCell>
                        <TableCell className="text-right font-mono text-sm">
                          {fmt(entry.totalCredit)}
                        </TableCell>
                        <TableCell>
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
        )}
      </div>
    </DashboardLayout>
  );
};

export default LedgerPage;
