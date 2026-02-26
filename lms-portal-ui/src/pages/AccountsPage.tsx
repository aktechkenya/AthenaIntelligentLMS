import { DashboardLayout } from "@/components/DashboardLayout";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Badge } from "@/components/ui/badge";
import { useQuery } from "@tanstack/react-query";
import { accountService, type Account } from "@/services/accountService";

function formatBalance(amount: number | undefined, currency: string): string {
  if (amount == null) return "—";
  return `${currency} ${amount.toLocaleString("en", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })}`;
}

const AccountsPage = () => {
  const { data: apiPage, isLoading } = useQuery({
    queryKey: ["accounts", "list"],
    queryFn: () => accountService.listAccounts(0, 50),
    staleTime: 60_000,
    retry: false,
  });

  const accounts: Account[] = apiPage?.content ?? [];

  return (
    <DashboardLayout
      title="Account Services"
      subtitle="Deposits, wallets & account management"
    >
      <div className="space-y-4 animate-fade-in">
        {/* Account count banner */}
        <div className="text-sm text-muted-foreground font-sans">
          {isLoading
            ? "Loading accounts..."
            : `${apiPage?.totalElements?.toLocaleString() ?? accounts.length} accounts`}
        </div>

        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="text-sm font-medium">
              Account Directory
            </CardTitle>
          </CardHeader>
          <CardContent className="p-0">
            {isLoading ? (
              <div className="p-4 space-y-2">
                {Array.from({ length: 5 }).map((_, i) => (
                  <Skeleton key={i} className="h-10 w-full" />
                ))}
              </div>
            ) : accounts.length === 0 ? (
              <div className="flex flex-col items-center justify-center h-48 text-muted-foreground">
                <p className="text-sm font-medium">No accounts found</p>
                <p className="text-xs mt-1">No account records returned from the backend.</p>
              </div>
            ) : (
              <Table>
                <TableHeader>
                  <TableRow className="hover:bg-transparent">
                    <TableHead className="text-[10px] uppercase tracking-wider">
                      Account ID
                    </TableHead>
                    <TableHead className="text-[10px] uppercase tracking-wider">
                      Account Holder
                    </TableHead>
                    <TableHead className="text-[10px] uppercase tracking-wider">
                      Type
                    </TableHead>
                    <TableHead className="text-[10px] uppercase tracking-wider text-right">
                      Balance
                    </TableHead>
                    <TableHead className="text-[10px] uppercase tracking-wider">
                      Status
                    </TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {accounts.map((acc) => (
                    <TableRow
                      key={acc.id}
                      className="table-row-hover cursor-pointer"
                    >
                      <TableCell className="text-xs font-mono">
                        {acc.accountNumber}
                      </TableCell>
                      <TableCell className="text-xs font-medium">
                        {acc.customerId}
                      </TableCell>
                      <TableCell className="text-xs text-muted-foreground">
                        {acc.accountType}
                      </TableCell>
                      <TableCell className="text-xs font-medium text-right">
                        {formatBalance(acc.balance, acc.currency)}
                      </TableCell>
                      <TableCell>
                        <Badge
                          variant="outline"
                          className={`text-[10px] font-semibold ${
                            acc.status === "Active" ||
                            acc.status === "ACTIVE"
                              ? "bg-success/15 text-success border-success/30"
                              : "bg-muted text-muted-foreground"
                          }`}
                        >
                          {acc.status}
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

export default AccountsPage;
