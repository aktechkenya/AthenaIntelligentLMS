import { useParams, useNavigate } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { DashboardLayout } from "@/components/DashboardLayout";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { formatKES } from "@/lib/format";
import { ArrowLeft, User, Wallet, FileText, Mail, Phone, MapPin, Shield } from "lucide-react";
import { customerService } from "@/services/customerService";
import { accountService } from "@/services/accountService";
import { loanManagementService } from "@/services/loanManagementService";

const statusColors: Record<string, string> = {
  ACTIVE: "bg-success/15 text-success border-success/30",
  INACTIVE: "bg-muted text-muted-foreground border-border",
  SUSPENDED: "bg-destructive/15 text-destructive border-destructive/30",
  BLOCKED: "bg-destructive/15 text-destructive border-destructive/30",
  FROZEN: "bg-warning/15 text-warning border-warning/30",
  DORMANT: "bg-muted text-muted-foreground border-border",
  CLOSED: "bg-muted text-muted-foreground border-border",
};

const loanStatusCls: Record<string, string> = {
  ACTIVE: "bg-success/15 text-success border-success/30",
  DISBURSED: "bg-success/15 text-success border-success/30",
  CLOSED: "bg-muted text-muted-foreground border-border",
  DEFAULTED: "bg-destructive/15 text-destructive border-destructive/30",
  WRITTEN_OFF: "bg-destructive/15 text-destructive border-destructive/30",
};

const kycColors: Record<string, string> = {
  VERIFIED: "bg-success/15 text-success border-success/30",
  PENDING: "bg-warning/15 text-warning border-warning/30",
  REJECTED: "bg-destructive/15 text-destructive border-destructive/30",
};

const Customer360Page = () => {
  const { customerId } = useParams<{ customerId: string }>();
  const navigate = useNavigate();

  const { data: customer, isLoading: custLoading } = useQuery({
    queryKey: ["customer", customerId],
    queryFn: () => customerService.getCustomer(customerId!),
    enabled: !!customerId,
  });

  const { data: accounts } = useQuery({
    queryKey: ["customer-accounts", customer?.customerId],
    queryFn: () => accountService.getCustomerAccounts(customer!.customerId),
    enabled: !!customer?.customerId,
  });

  const { data: loansData } = useQuery({
    queryKey: ["loans-all", 0, 100],
    queryFn: () => loanManagementService.listLoans(0, 100),
    enabled: !!customer?.customerId,
  });

  const customerLoans = (loansData?.content ?? []).filter(
    (l) => l.customerId === customer?.customerId
  );

  if (!customerId) {
    return (
      <DashboardLayout title="Customer 360" subtitle="Customer detail view"
        breadcrumbs={[{ label: "Home", href: "/" }, { label: "Customers", href: "/borrowers" }]}>
        <Card>
          <CardContent className="p-8 text-center text-muted-foreground font-sans">
            Enter a customer ID to view details.
          </CardContent>
        </Card>
      </DashboardLayout>
    );
  }

  return (
    <DashboardLayout title="Customer 360"
      subtitle={customer ? `${customer.firstName} ${customer.lastName}` : "Loading..."}
      breadcrumbs={[
        { label: "Home", href: "/" },
        { label: "Customers", href: "/borrowers" },
        { label: customer?.customerId ?? customerId },
      ]}>
      <div className="space-y-4">
        <Button variant="ghost" size="sm" className="text-xs font-sans"
          onClick={() => navigate("/borrowers")}>
          <ArrowLeft className="h-3.5 w-3.5 mr-1" /> Back to Directory
        </Button>

        {custLoading ? (
          <div className="flex items-center justify-center h-32 text-muted-foreground text-xs">
            Loading customer...
          </div>
        ) : !customer ? (
          <div className="flex items-center justify-center h-32 text-destructive text-xs">
            Customer not found.
          </div>
        ) : (
          <>
            {/* Profile Card */}
            <Card>
              <CardContent className="p-5">
                <div className="flex items-start gap-5">
                  <div className="h-14 w-14 rounded-full bg-primary/10 flex items-center justify-center shrink-0">
                    <User className="h-7 w-7 text-primary" />
                  </div>
                  <div className="flex-1 grid grid-cols-1 sm:grid-cols-3 gap-4">
                    <div>
                      <p className="text-[10px] text-muted-foreground uppercase tracking-wider">Name</p>
                      <p className="text-sm font-semibold mt-0.5">{customer.firstName} {customer.lastName}</p>
                    </div>
                    <div>
                      <p className="text-[10px] text-muted-foreground uppercase tracking-wider">Customer ID</p>
                      <p className="text-sm font-mono mt-0.5">{customer.customerId}</p>
                    </div>
                    <div>
                      <p className="text-[10px] text-muted-foreground uppercase tracking-wider">Type</p>
                      <p className="text-sm mt-0.5">{customer.customerType}</p>
                    </div>
                    {customer.phone && (
                      <div className="flex items-center gap-1.5">
                        <Phone className="h-3 w-3 text-muted-foreground" />
                        <span className="text-xs">{customer.phone}</span>
                      </div>
                    )}
                    {customer.email && (
                      <div className="flex items-center gap-1.5">
                        <Mail className="h-3 w-3 text-muted-foreground" />
                        <span className="text-xs">{customer.email}</span>
                      </div>
                    )}
                    {customer.address && (
                      <div className="flex items-center gap-1.5">
                        <MapPin className="h-3 w-3 text-muted-foreground" />
                        <span className="text-xs">{customer.address}</span>
                      </div>
                    )}
                  </div>
                  <div className="flex flex-col items-end gap-2">
                    <Badge variant="outline"
                      className={`text-[10px] font-semibold ${statusColors[customer.status] ?? ""}`}>
                      {customer.status}
                    </Badge>
                    <Badge variant="outline"
                      className={`text-[10px] font-semibold ${kycColors[customer.kycStatus ?? ""] ?? ""}`}>
                      <Shield className="h-2.5 w-2.5 mr-1" />
                      KYC: {customer.kycStatus ?? "—"}
                    </Badge>
                  </div>
                </div>
              </CardContent>
            </Card>

            {/* Accounts */}
            <Card>
              <CardHeader className="pb-2">
                <CardTitle className="text-xs font-semibold uppercase tracking-wider flex items-center gap-1.5">
                  <Wallet className="h-3.5 w-3.5" /> Accounts ({accounts?.length ?? 0})
                </CardTitle>
              </CardHeader>
              <CardContent className="p-0">
                {!accounts || accounts.length === 0 ? (
                  <div className="p-4 text-center text-xs text-muted-foreground">
                    No accounts linked to this customer.
                  </div>
                ) : (
                  <Table>
                    <TableHeader>
                      <TableRow className="hover:bg-transparent">
                        <TableHead className="text-[10px] uppercase tracking-wider">Account Number</TableHead>
                        <TableHead className="text-[10px] uppercase tracking-wider">Type</TableHead>
                        <TableHead className="text-[10px] uppercase tracking-wider">Currency</TableHead>
                        <TableHead className="text-[10px] uppercase tracking-wider">Status</TableHead>
                        <TableHead className="text-[10px] uppercase tracking-wider">Created</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {accounts.map((acc) => (
                        <TableRow key={acc.id} className="table-row-hover">
                          <TableCell className="text-xs font-mono">{acc.accountNumber}</TableCell>
                          <TableCell className="text-xs">{acc.accountType}</TableCell>
                          <TableCell className="text-xs font-mono">{acc.currency}</TableCell>
                          <TableCell>
                            <Badge variant="outline"
                              className={`text-[10px] ${statusColors[acc.status] ?? ""}`}>
                              {acc.status}
                            </Badge>
                          </TableCell>
                          <TableCell className="text-xs text-muted-foreground">
                            {acc.createdAt ? new Date(acc.createdAt).toLocaleDateString() : "—"}
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                )}
              </CardContent>
            </Card>

            {/* Loans */}
            <Card>
              <CardHeader className="pb-2">
                <CardTitle className="text-xs font-semibold uppercase tracking-wider flex items-center gap-1.5">
                  <FileText className="h-3.5 w-3.5" /> Loans ({customerLoans.length})
                </CardTitle>
              </CardHeader>
              <CardContent className="p-0">
                {customerLoans.length === 0 ? (
                  <div className="p-4 text-center text-xs text-muted-foreground">
                    No loans associated with this customer.
                  </div>
                ) : (
                  <Table>
                    <TableHeader>
                      <TableRow className="hover:bg-transparent">
                        <TableHead className="text-[10px] uppercase tracking-wider">Loan ID</TableHead>
                        <TableHead className="text-[10px] uppercase tracking-wider">Product</TableHead>
                        <TableHead className="text-[10px] uppercase tracking-wider text-right">Disbursed</TableHead>
                        <TableHead className="text-[10px] uppercase tracking-wider text-right">Outstanding</TableHead>
                        <TableHead className="text-[10px] uppercase tracking-wider text-center">DPD</TableHead>
                        <TableHead className="text-[10px] uppercase tracking-wider">Status</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {customerLoans.map((loan) => (
                        <TableRow key={loan.id} className="table-row-hover cursor-pointer"
                          onClick={() => navigate(`/loan/${loan.id}`)}>
                          <TableCell className="text-xs font-mono text-info">{loan.id}</TableCell>
                          <TableCell className="text-xs text-muted-foreground">{loan.productId ?? "—"}</TableCell>
                          <TableCell className="text-xs font-mono text-right">{formatKES(loan.disbursedAmount)}</TableCell>
                          <TableCell className="text-xs font-mono text-right">{formatKES(loan.outstandingPrincipal)}</TableCell>
                          <TableCell className={`text-xs font-mono text-center font-semibold ${
                            loan.dpd > 30 ? "text-destructive" : loan.dpd > 0 ? "text-warning" : "text-foreground"
                          }`}>{loan.dpd}</TableCell>
                          <TableCell>
                            <Badge variant="outline"
                              className={`text-[9px] capitalize ${loanStatusCls[loan.status] ?? "bg-muted text-muted-foreground border-border"}`}>
                              {loan.status}
                            </Badge>
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                )}
              </CardContent>
            </Card>
          </>
        )}
      </div>
    </DashboardLayout>
  );
};

export default Customer360Page;
