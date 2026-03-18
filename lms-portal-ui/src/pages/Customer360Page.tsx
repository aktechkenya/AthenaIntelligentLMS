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
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { formatKES } from "@/lib/format";
import { ArrowLeft, User, Wallet, FileText, Mail, Phone, MapPin, Shield, AlertTriangle, Network, Briefcase, Zap } from "lucide-react";
import { customerService } from "@/services/customerService";
import { accountService } from "@/services/accountService";
import { loanManagementService } from "@/services/loanManagementService";
import { fraudService, type CustomerRiskProfile, type FraudAlert, type NetworkNode, type FraudCase, type ScoringHistoryEntry } from "@/services/fraudService";

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

const severityColors: Record<string, string> = {
  LOW: "bg-muted text-muted-foreground border-border",
  MEDIUM: "bg-warning/15 text-warning border-warning/30",
  HIGH: "bg-orange-100 text-orange-700 border-orange-300",
  CRITICAL: "bg-destructive/15 text-destructive border-destructive/30",
};

const riskColors: Record<string, string> = {
  LOW: "text-success",
  MEDIUM: "text-warning",
  HIGH: "text-orange-600",
  CRITICAL: "text-destructive",
};

const alertStatusColors: Record<string, string> = {
  OPEN: "bg-destructive/15 text-destructive border-destructive/30",
  UNDER_REVIEW: "bg-warning/15 text-warning border-warning/30",
  ESCALATED: "bg-orange-100 text-orange-700 border-orange-300",
  CONFIRMED_FRAUD: "bg-destructive/15 text-destructive border-destructive/30",
  FALSE_POSITIVE: "bg-muted text-muted-foreground border-border",
  CLOSED: "bg-muted text-muted-foreground border-border",
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

  const { data: riskProfile } = useQuery({
    queryKey: ["customer-risk", customer?.customerId],
    queryFn: () => fraudService.getCustomerRisk(customer!.customerId),
    enabled: !!customer?.customerId,
    retry: false,
  });

  const { data: alertsData } = useQuery({
    queryKey: ["customer-alerts", customer?.customerId],
    queryFn: () => fraudService.listCustomerAlerts(customer!.customerId, 0, 50),
    enabled: !!customer?.customerId,
    retry: false,
  });

  const { data: networkData } = useQuery({
    queryKey: ["customer-network", customer?.customerId],
    queryFn: () => fraudService.getCustomerNetwork(customer!.customerId),
    enabled: !!customer?.customerId,
    retry: false,
  });

  const { data: casesData } = useQuery({
    queryKey: ["customer-fraud-cases", customer?.customerId],
    queryFn: () => fraudService.listCases(0, 50),
    enabled: !!customer?.customerId,
    retry: false,
  });

  const { data: scoringHistoryData } = useQuery({
    queryKey: ["customer-scoring-history", customer?.customerId],
    queryFn: () => fraudService.getCustomerScoringHistory(customer!.customerId, 0, 20),
    enabled: !!customer?.customerId,
    retry: false,
  });

  const customerLoans = (loansData?.content ?? []).filter(
    (l) => l.customerId === customer?.customerId
  );

  const customerAlerts = alertsData?.content ?? [];
  const customerCases = (casesData?.content ?? []).filter(
    (c) => c.customerId === customer?.customerId
  );
  const scoringHistory = scoringHistoryData?.content ?? [];

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
                    {riskProfile && (
                      <Badge variant="outline"
                        className={`text-[10px] font-semibold ${severityColors[riskProfile.riskLevel] ?? ""}`}>
                        <AlertTriangle className="h-2.5 w-2.5 mr-1" />
                        Risk: {riskProfile.riskLevel}
                      </Badge>
                    )}
                  </div>
                </div>
              </CardContent>
            </Card>

            <Tabs defaultValue="accounts" className="w-full">
              <TabsList>
                <TabsTrigger value="accounts" className="text-xs">Accounts</TabsTrigger>
                <TabsTrigger value="loans" className="text-xs">Loans ({customerLoans.length})</TabsTrigger>
                <TabsTrigger value="fraud" className="text-xs">
                  Fraud & Risk
                  {riskProfile && (riskProfile.riskLevel === "HIGH" || riskProfile.riskLevel === "CRITICAL") && (
                    <span className="ml-1.5 h-2 w-2 rounded-full bg-destructive inline-block" />
                  )}
                </TabsTrigger>
              </TabsList>

              {/* Accounts Tab */}
              <TabsContent value="accounts">
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
              </TabsContent>

              {/* Loans Tab */}
              <TabsContent value="loans">
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
              </TabsContent>

              {/* Fraud & Risk Tab */}
              <TabsContent value="fraud">
                <div className="space-y-4">
                  {/* Risk Profile */}
                  <Card>
                    <CardHeader className="pb-2">
                      <CardTitle className="text-xs font-semibold uppercase tracking-wider flex items-center gap-1.5">
                        <Shield className="h-3.5 w-3.5" /> Risk Profile
                      </CardTitle>
                    </CardHeader>
                    <CardContent>
                      {!riskProfile ? (
                        <p className="text-xs text-muted-foreground">No risk profile available.</p>
                      ) : (
                        <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
                          <div>
                            <p className="text-[10px] text-muted-foreground uppercase tracking-wider">Risk Score</p>
                            <p className={`text-2xl font-bold font-mono ${riskColors[riskProfile.riskLevel] ?? ""}`}>
                              {riskProfile.riskScore}
                            </p>
                            <Badge variant="outline" className={`text-[9px] mt-1 ${severityColors[riskProfile.riskLevel] ?? ""}`}>
                              {riskProfile.riskLevel}
                            </Badge>
                          </div>
                          <div>
                            <p className="text-[10px] text-muted-foreground uppercase tracking-wider">Total Alerts</p>
                            <p className="text-2xl font-bold font-mono">{riskProfile.totalAlerts}</p>
                            <p className="text-[10px] text-muted-foreground">{riskProfile.openAlerts} open</p>
                          </div>
                          <div>
                            <p className="text-[10px] text-muted-foreground uppercase tracking-wider">Confirmed Fraud</p>
                            <p className="text-2xl font-bold font-mono text-destructive">{riskProfile.confirmedFraud}</p>
                          </div>
                          <div>
                            <p className="text-[10px] text-muted-foreground uppercase tracking-wider">False Positives</p>
                            <p className="text-2xl font-bold font-mono text-muted-foreground">{riskProfile.falsePositives}</p>
                          </div>
                        </div>
                      )}
                    </CardContent>
                  </Card>

                  {/* Fraud Alerts */}
                  <Card>
                    <CardHeader className="pb-2">
                      <CardTitle className="text-xs font-semibold uppercase tracking-wider flex items-center gap-1.5">
                        <AlertTriangle className="h-3.5 w-3.5" /> Fraud Alerts ({customerAlerts.length})
                      </CardTitle>
                    </CardHeader>
                    <CardContent className="p-0">
                      {customerAlerts.length === 0 ? (
                        <div className="p-4 text-center text-xs text-muted-foreground">
                          No fraud alerts for this customer.
                        </div>
                      ) : (
                        <Table>
                          <TableHeader>
                            <TableRow className="hover:bg-transparent">
                              <TableHead className="text-[10px] uppercase tracking-wider">Type</TableHead>
                              <TableHead className="text-[10px] uppercase tracking-wider">Severity</TableHead>
                              <TableHead className="text-[10px] uppercase tracking-wider">Status</TableHead>
                              <TableHead className="text-[10px] uppercase tracking-wider">Description</TableHead>
                              <TableHead className="text-[10px] uppercase tracking-wider">Date</TableHead>
                            </TableRow>
                          </TableHeader>
                          <TableBody>
                            {customerAlerts.map((alert) => (
                              <TableRow key={alert.id} className="table-row-hover cursor-pointer"
                                onClick={() => navigate("/fraud")}>
                                <TableCell className="text-xs font-mono">{alert.alertType}</TableCell>
                                <TableCell>
                                  <Badge variant="outline" className={`text-[9px] ${severityColors[alert.severity] ?? ""}`}>
                                    {alert.severity}
                                  </Badge>
                                </TableCell>
                                <TableCell>
                                  <Badge variant="outline" className={`text-[9px] ${alertStatusColors[alert.status] ?? ""}`}>
                                    {alert.status}
                                  </Badge>
                                </TableCell>
                                <TableCell className="text-xs max-w-[300px] truncate">{alert.description}</TableCell>
                                <TableCell className="text-xs text-muted-foreground">
                                  {new Date(alert.createdAt).toLocaleDateString()}
                                </TableCell>
                              </TableRow>
                            ))}
                          </TableBody>
                        </Table>
                      )}
                    </CardContent>
                  </Card>

                  {/* Network Links */}
                  <Card>
                    <CardHeader className="pb-2">
                      <CardTitle className="text-xs font-semibold uppercase tracking-wider flex items-center gap-1.5">
                        <Network className="h-3.5 w-3.5" /> Network Links
                      </CardTitle>
                    </CardHeader>
                    <CardContent>
                      {!networkData || networkData.links.length === 0 ? (
                        <p className="text-xs text-muted-foreground">No network links detected.</p>
                      ) : (
                        <div className="space-y-2">
                          <p className="text-xs text-muted-foreground">
                            {networkData.linkCount} connection{networkData.linkCount !== 1 ? "s" : ""} detected
                          </p>
                          <Table>
                            <TableHeader>
                              <TableRow className="hover:bg-transparent">
                                <TableHead className="text-[10px] uppercase tracking-wider">Linked Customer</TableHead>
                                <TableHead className="text-[10px] uppercase tracking-wider">Link Type</TableHead>
                                <TableHead className="text-[10px] uppercase tracking-wider">Value</TableHead>
                                <TableHead className="text-[10px] uppercase tracking-wider">Flagged</TableHead>
                              </TableRow>
                            </TableHeader>
                            <TableBody>
                              {networkData.links.map((link, idx) => (
                                <TableRow key={idx} className="table-row-hover">
                                  <TableCell className="text-xs font-mono text-info cursor-pointer"
                                    onClick={() => navigate(`/customer/${link.linkedCustomerId}`)}>
                                    {link.linkedCustomerId}
                                  </TableCell>
                                  <TableCell className="text-xs">{link.linkType.replace("SHARED_", "")}</TableCell>
                                  <TableCell className="text-xs font-mono">{link.linkValue}</TableCell>
                                  <TableCell>
                                    {link.flagged ? (
                                      <Badge variant="outline" className="text-[9px] bg-destructive/15 text-destructive border-destructive/30">
                                        Flagged
                                      </Badge>
                                    ) : (
                                      <span className="text-xs text-muted-foreground">—</span>
                                    )}
                                  </TableCell>
                                </TableRow>
                              ))}
                            </TableBody>
                          </Table>
                        </div>
                      )}
                    </CardContent>
                  </Card>

                  {/* ML Scoring History */}
                  <Card>
                    <CardHeader className="pb-2">
                      <CardTitle className="text-xs font-semibold uppercase tracking-wider flex items-center gap-1.5">
                        <Zap className="h-3.5 w-3.5" /> ML Scoring History ({scoringHistory.length})
                      </CardTitle>
                    </CardHeader>
                    <CardContent className="p-0">
                      {scoringHistory.length === 0 ? (
                        <div className="p-4 text-center text-xs text-muted-foreground">
                          No ML scoring records for this customer.
                        </div>
                      ) : (
                        <Table>
                          <TableHeader>
                            <TableRow className="hover:bg-transparent">
                              <TableHead className="text-[10px] uppercase tracking-wider">Event</TableHead>
                              <TableHead className="text-[10px] uppercase tracking-wider">ML Score</TableHead>
                              <TableHead className="text-[10px] uppercase tracking-wider">Risk</TableHead>
                              <TableHead className="text-[10px] uppercase tracking-wider text-right">Amount</TableHead>
                              <TableHead className="text-[10px] uppercase tracking-wider">Latency</TableHead>
                              <TableHead className="text-[10px] uppercase tracking-wider">Date</TableHead>
                            </TableRow>
                          </TableHeader>
                          <TableBody>
                            {scoringHistory.map((s) => (
                              <TableRow key={s.id} className="table-row-hover">
                                <TableCell className="text-xs font-mono">{s.eventType ?? "—"}</TableCell>
                                <TableCell className="text-xs font-mono font-semibold">{s.mlScore.toFixed(3)}</TableCell>
                                <TableCell>
                                  <Badge variant="outline" className={`text-[9px] ${severityColors[s.riskLevel] ?? ""}`}>
                                    {s.riskLevel}
                                  </Badge>
                                </TableCell>
                                <TableCell className="text-xs font-mono text-right">
                                  {s.amount != null ? Number(s.amount).toLocaleString() : "—"}
                                </TableCell>
                                <TableCell className="text-xs text-muted-foreground">
                                  {s.latencyMs != null ? `${s.latencyMs.toFixed(0)}ms` : "—"}
                                </TableCell>
                                <TableCell className="text-xs text-muted-foreground">
                                  {new Date(s.createdAt).toLocaleDateString()}
                                </TableCell>
                              </TableRow>
                            ))}
                          </TableBody>
                        </Table>
                      )}
                    </CardContent>
                  </Card>

                  {/* Investigation Cases */}
                  <Card>
                    <CardHeader className="pb-2">
                      <CardTitle className="text-xs font-semibold uppercase tracking-wider flex items-center gap-1.5">
                        <Briefcase className="h-3.5 w-3.5" /> Investigation Cases ({customerCases.length})
                      </CardTitle>
                    </CardHeader>
                    <CardContent className="p-0">
                      {customerCases.length === 0 ? (
                        <div className="p-4 text-center text-xs text-muted-foreground">
                          No investigation cases for this customer.
                        </div>
                      ) : (
                        <Table>
                          <TableHeader>
                            <TableRow className="hover:bg-transparent">
                              <TableHead className="text-[10px] uppercase tracking-wider">Case #</TableHead>
                              <TableHead className="text-[10px] uppercase tracking-wider">Title</TableHead>
                              <TableHead className="text-[10px] uppercase tracking-wider">Priority</TableHead>
                              <TableHead className="text-[10px] uppercase tracking-wider">Status</TableHead>
                              <TableHead className="text-[10px] uppercase tracking-wider">Opened</TableHead>
                            </TableRow>
                          </TableHeader>
                          <TableBody>
                            {customerCases.map((c) => (
                              <TableRow key={c.id} className="table-row-hover cursor-pointer"
                                onClick={() => navigate("/fraud-cases")}>
                                <TableCell className="text-xs font-mono text-info">{c.caseNumber}</TableCell>
                                <TableCell className="text-xs max-w-[250px] truncate">{c.title}</TableCell>
                                <TableCell>
                                  <Badge variant="outline" className={`text-[9px] ${severityColors[c.priority] ?? ""}`}>
                                    {c.priority}
                                  </Badge>
                                </TableCell>
                                <TableCell>
                                  <Badge variant="outline" className={`text-[9px] ${alertStatusColors[c.status] ?? ""}`}>
                                    {c.status.replace(/_/g, " ")}
                                  </Badge>
                                </TableCell>
                                <TableCell className="text-xs text-muted-foreground">
                                  {new Date(c.createdAt).toLocaleDateString()}
                                </TableCell>
                              </TableRow>
                            ))}
                          </TableBody>
                        </Table>
                      )}
                    </CardContent>
                  </Card>
                </div>
              </TabsContent>
            </Tabs>
          </>
        )}
      </div>
    </DashboardLayout>
  );
};

export default Customer360Page;
