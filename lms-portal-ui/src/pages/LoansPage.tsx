import { useState } from "react";
import { DashboardLayout } from "@/components/DashboardLayout";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from "@/components/ui/table";
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from "@/components/ui/select";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import {
  Sheet, SheetContent,
} from "@/components/ui/sheet";
import { Textarea } from "@/components/ui/textarea";
import { Progress } from "@/components/ui/progress";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Search, Download, Plus, LayoutGrid, List, Eye,
  CheckCircle2, Clock, XCircle, FileText, AlertTriangle,
  User, Phone, Hash, Calendar, MapPin, Brain,
} from "lucide-react";
import { motion, AnimatePresence } from "framer-motion";
import { formatKES } from "@/lib/format";
import { useQuery } from "@tanstack/react-query";
import { loanOriginationService, type LoanApplication as ApiLoanApplication } from "@/services/loanOriginationService";

// ─── Local Types ─────────────────────────────────────
type ApplicationStage = "received" | "kyc_pending" | "under_assessment" | "credit_committee" | "approved" | "rejected";

interface LoanApplication {
  id: string;
  customerName: string;
  customerInitials: string;
  amount: number;
  product: string;
  productColor: string;
  riskScore: number;
  stage: ApplicationStage;
  daysInStage: number;
  analyst: string;
  date: string;
  phone: string;
  nationalId: string;
  channel: string;
  purpose: string;
  tenor: number;
  bureauScore: number;
  behaviouralScore: number;
  incomeScore: number;
  compositeScore: number;
  compositeLabel: string;
  dscr: number;
  dti: string | number;
  aiRecommendation: string;
  aiConfidence: number;
  documents: { name: string; status: "verified" | "pending" | "missing" }[];
}

const stageConfig: Record<ApplicationStage, { label: string; color: string }> = {
  received: { label: "Received", color: "bg-info/15 text-info border-info/30" },
  kyc_pending: { label: "KYC Pending", color: "bg-warning/15 text-warning border-warning/30" },
  under_assessment: { label: "Under Assessment", color: "bg-accent/15 text-accent-foreground border-accent/30" },
  credit_committee: { label: "Credit Committee", color: "bg-[hsl(var(--navy-700))]/15 text-[hsl(var(--navy-700))] border-[hsl(var(--navy-700))]/30" },
  approved: { label: "Approved", color: "bg-success/15 text-success border-success/30" },
  rejected: { label: "Rejected", color: "bg-destructive/10 text-destructive border-destructive/20" },
};

/** Map backend status to stage key */
function statusToStage(status: string): ApplicationStage {
  const map: Record<string, ApplicationStage> = {
    DRAFT: "received",
    SUBMITTED: "kyc_pending",
    UNDER_REVIEW: "under_assessment",
    PENDING_APPROVAL: "credit_committee",
    APPROVED: "approved",
    REJECTED: "rejected",
    DISBURSED: "approved",
    CANCELLED: "rejected",
  };
  return map[status?.toUpperCase()] ?? "received";
}

/** Adapt a backend LoanApplication into the UI shape */
function adaptApplication(app: ApiLoanApplication): LoanApplication {
  const stage = statusToStage(app.status);
  return {
    id: app.id,
    customerName: app.customerId,
    customerInitials: app.customerId.slice(0, 2).toUpperCase(),
    amount: app.requestedAmount,
    product: app.productId,
    productColor: "bg-info/15 text-info border-info/30",
    stage,
    riskScore: app.creditScore ?? 650,
    daysInStage: 0,
    analyst: "Unassigned",
    date: app.createdAt ? app.createdAt.split("T")[0] : "—",
    phone: "—",
    nationalId: "—",
    channel: "Digital",
    tenor: app.tenorMonths,
    purpose: app.purpose ?? "—",
    compositeScore: app.creditScore ?? 650,
    compositeLabel: "Standard",
    bureauScore: 0,
    behaviouralScore: 0,
    incomeScore: 0,
    dscr: 0,
    dti: "0",
    aiRecommendation: "APPROVAL",
    aiConfidence: 80,
    documents: [],
  };
}

const stageOrder: ApplicationStage[] = ["received", "kyc_pending", "under_assessment", "credit_committee", "approved", "rejected"];

const LoansPage = () => {
  const [viewMode, setViewMode] = useState<"kanban" | "list">("kanban");
  const [searchQuery, setSearchQuery] = useState("");
  const [stageFilter, setStageFilter] = useState("all");
  const [selectedApp, setSelectedApp] = useState<LoanApplication | null>(null);
  const [drawerOpen, setDrawerOpen] = useState(false);

  const { data: apiPage, isLoading } = useQuery({
    queryKey: ["loan-applications", "list"],
    queryFn: () => loanOriginationService.listApplications(0, 100),
    staleTime: 60_000,
    retry: false,
  });

  const allApplications: LoanApplication[] =
    apiPage && apiPage.content.length > 0
      ? apiPage.content.map(adaptApplication)
      : [];

  const filteredApps = allApplications.filter((app) => {
    if (searchQuery) {
      const q = searchQuery.toLowerCase();
      if (!app.customerName.toLowerCase().includes(q) && !app.id.toLowerCase().includes(q)) return false;
    }
    if (stageFilter !== "all" && app.stage !== stageFilter) return false;
    return true;
  });

  const openDetail = (app: LoanApplication) => {
    setSelectedApp(app);
    setDrawerOpen(true);
  };

  const stageCounts = stageOrder.reduce((acc, stage) => {
    acc[stage] = filteredApps.filter(a => a.stage === stage).length;
    return acc;
  }, {} as Record<ApplicationStage, number>);

  return (
    <DashboardLayout
      title="Loan Applications"
      subtitle="Full application pipeline — Kanban & list views"
      breadcrumbs={[{ label: "Home", href: "/" }, { label: "Lending" }, { label: "Loan Applications" }]}
    >
      <div className="space-y-4">
        {/* Action bar */}
        <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3">
          <div className="flex items-center gap-2 w-full sm:w-auto">
            <div className="relative flex-1 sm:w-72">
              <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-muted-foreground" />
              <Input
                placeholder="Search by ID, customer name..."
                className="pl-8 h-9 text-xs font-sans"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
              />
            </div>
            <Select value={stageFilter} onValueChange={setStageFilter}>
              <SelectTrigger className="w-40 h-9 text-xs font-sans">
                <SelectValue placeholder="Stage" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">All Stages</SelectItem>
                {stageOrder.map(s => (
                  <SelectItem key={s} value={s}>{stageConfig[s].label}</SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <div className="flex items-center gap-2">
            <div className="flex border border-border rounded-md overflow-hidden">
              <button
                onClick={() => setViewMode("kanban")}
                className={`p-2 transition-colors ${viewMode === "kanban" ? "bg-primary text-primary-foreground" : "bg-card text-muted-foreground hover:bg-muted"}`}
              >
                <LayoutGrid className="h-3.5 w-3.5" />
              </button>
              <button
                onClick={() => setViewMode("list")}
                className={`p-2 transition-colors ${viewMode === "list" ? "bg-primary text-primary-foreground" : "bg-card text-muted-foreground hover:bg-muted"}`}
              >
                <List className="h-3.5 w-3.5" />
              </button>
            </div>
            <Button variant="outline" size="sm" className="text-xs font-sans">
              <Download className="mr-1.5 h-3.5 w-3.5" /> Export
            </Button>
            <Button size="sm" className="text-xs font-sans bg-primary hover:bg-primary/90">
              <Plus className="mr-1.5 h-3.5 w-3.5" /> New Application
            </Button>
          </div>
        </div>

        {isLoading && (
          <div className="flex gap-3 overflow-x-auto pb-4">
            {stageOrder.map((stage) => (
              <div key={stage} className="min-w-[260px] w-[260px] shrink-0 space-y-2">
                <Skeleton className="h-6 w-32" />
                {Array.from({ length: 3 }).map((_, i) => (
                  <Skeleton key={i} className="h-24 w-full" />
                ))}
              </div>
            ))}
          </div>
        )}

        {/* Kanban View */}
        {!isLoading && viewMode === "kanban" && (
          <div className="flex gap-3 overflow-x-auto pb-4">
            {stageOrder.map((stage) => {
              const config = stageConfig[stage];
              const apps = filteredApps.filter(a => a.stage === stage);
              return (
                <div key={stage} className="min-w-[260px] w-[260px] shrink-0">
                  <div className="flex items-center justify-between mb-2 px-1">
                    <div className="flex items-center gap-2">
                      <span className="text-xs font-sans font-semibold">{config.label}</span>
                      <Badge variant="outline" className="text-[10px] font-mono h-5 min-w-[24px] justify-center">
                        {stageCounts[stage]}
                      </Badge>
                    </div>
                  </div>
                  <div className="space-y-2 max-h-[calc(100vh-280px)] overflow-y-auto pr-1">
                    <AnimatePresence>
                      {apps.map((app) => (
                        <motion.div
                          key={app.id}
                          layout
                          initial={{ opacity: 0, scale: 0.95 }}
                          animate={{ opacity: 1, scale: 1 }}
                          exit={{ opacity: 0, scale: 0.95 }}
                          className="kanban-card"
                          onClick={() => openDetail(app)}
                        >
                          <div className="flex items-center gap-2 mb-2">
                            <div className="h-7 w-7 rounded-full bg-primary/10 flex items-center justify-center text-[10px] font-semibold text-primary font-sans shrink-0">
                              {app.customerInitials}
                            </div>
                            <div className="flex-1 min-w-0">
                              <p className="text-xs font-sans font-medium truncate">{app.customerName}</p>
                              <p className="text-[10px] text-muted-foreground font-mono">{app.id}</p>
                            </div>
                          </div>
                          <div className="flex items-center justify-between mb-2">
                            <span className="text-sm font-mono font-bold">{formatKES(app.amount)}</span>
                          </div>
                          <div className="flex items-center gap-1.5 mb-2">
                            <Badge variant="outline" className={`text-[9px] font-sans ${app.productColor}`}>{app.product}</Badge>
                          </div>
                          <div className="flex items-center justify-between text-[10px] text-muted-foreground font-sans">
                            <div className="flex items-center gap-1">
                              <div className="h-4 w-4 rounded-full border-2 flex items-center justify-center" style={{
                                borderColor: app.riskScore > 700 ? "hsl(160, 60%, 38%)" : app.riskScore > 600 ? "hsl(38, 92%, 48%)" : "hsl(0, 72%, 51%)"
                              }}>
                                <span className="text-[7px] font-mono font-bold">{Math.floor(app.riskScore / 10)}</span>
                              </div>
                              <span>{app.daysInStage}d in stage</span>
                            </div>
                            <span>{app.analyst.split(" ")[0]}</span>
                          </div>
                        </motion.div>
                      ))}
                    </AnimatePresence>
                    {apps.length === 0 && (
                      <div className="text-center py-8 text-xs text-muted-foreground font-sans">
                        No applications
                      </div>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        )}

        {/* List View */}
        {!isLoading && viewMode === "list" && (
          <Card>
            <CardContent className="p-0">
              {filteredApps.length === 0 ? (
                <div className="flex flex-col items-center justify-center h-48 text-muted-foreground">
                  <p className="text-sm font-medium">No applications found</p>
                  <p className="text-xs mt-1">No loan applications returned from the backend.</p>
                </div>
              ) : (
                <>
                  <Table>
                    <TableHeader>
                      <TableRow className="hover:bg-transparent">
                        <TableHead className="text-[10px] uppercase tracking-wider font-sans">Application ID</TableHead>
                        <TableHead className="text-[10px] uppercase tracking-wider font-sans">Customer</TableHead>
                        <TableHead className="text-[10px] uppercase tracking-wider font-sans">Product</TableHead>
                        <TableHead className="text-[10px] uppercase tracking-wider font-sans text-right">Amount</TableHead>
                        <TableHead className="text-[10px] uppercase tracking-wider font-sans">Score</TableHead>
                        <TableHead className="text-[10px] uppercase tracking-wider font-sans">Stage</TableHead>
                        <TableHead className="text-[10px] uppercase tracking-wider font-sans">Analyst</TableHead>
                        <TableHead className="text-[10px] uppercase tracking-wider font-sans">Date</TableHead>
                        <TableHead className="text-[10px] uppercase tracking-wider font-sans">Actions</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {filteredApps.slice(0, 25).map((app) => (
                        <TableRow key={app.id} className="table-row-hover cursor-pointer" onClick={() => openDetail(app)}>
                          <TableCell className="text-xs font-mono font-medium">{app.id}</TableCell>
                          <TableCell>
                            <div className="flex items-center gap-2">
                              <div className="h-6 w-6 rounded-full bg-primary/10 flex items-center justify-center text-[9px] font-semibold text-primary font-sans">
                                {app.customerInitials}
                              </div>
                              <span className="text-xs font-sans font-medium">{app.customerName}</span>
                            </div>
                          </TableCell>
                          <TableCell>
                            <Badge variant="outline" className={`text-[10px] font-sans ${app.productColor}`}>{app.product}</Badge>
                          </TableCell>
                          <TableCell className="text-xs font-mono font-medium text-right">{formatKES(app.amount)}</TableCell>
                          <TableCell>
                            <span className={`text-xs font-mono font-semibold ${app.riskScore > 700 ? "text-success" : app.riskScore > 600 ? "text-warning" : "text-destructive"}`}>
                              {app.riskScore}
                            </span>
                          </TableCell>
                          <TableCell>
                            <Badge variant="outline" className={`text-[10px] font-sans font-semibold ${stageConfig[app.stage].color}`}>
                              {stageConfig[app.stage].label}
                            </Badge>
                          </TableCell>
                          <TableCell className="text-xs font-sans text-muted-foreground">{app.analyst}</TableCell>
                          <TableCell className="text-xs font-sans text-muted-foreground">{app.date}</TableCell>
                          <TableCell>
                            <Button variant="ghost" size="sm" className="h-7 text-[10px] font-sans">
                              <Eye className="h-3 w-3 mr-1" /> View
                            </Button>
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                  <div className="flex items-center justify-between px-4 py-3 border-t text-xs text-muted-foreground font-sans">
                    <span>Showing {Math.min(25, filteredApps.length)} of {filteredApps.length} applications</span>
                    <div className="flex items-center gap-2">
                      <Button variant="outline" size="sm" className="h-7 text-[10px]" disabled>Previous</Button>
                      <Button variant="outline" size="sm" className="h-7 text-[10px]">Next</Button>
                    </div>
                  </div>
                </>
              )}
            </CardContent>
          </Card>
        )}
      </div>

      {/* ─── Application Detail Drawer ─── */}
      <Sheet open={drawerOpen} onOpenChange={setDrawerOpen}>
        <SheetContent className="w-full sm:max-w-[720px] overflow-y-auto p-0">
          {selectedApp && <ApplicationDetail app={selectedApp} onClose={() => setDrawerOpen(false)} />}
        </SheetContent>
      </Sheet>
    </DashboardLayout>
  );
};

// ─── Application Detail Component ───
function ApplicationDetail({ app, onClose }: { app: LoanApplication; onClose: () => void }) {
  const [activeTab, setActiveTab] = useState("summary");

  const stageSteps = ["received", "kyc_pending", "under_assessment", "credit_committee", "approved"];
  const currentStepIdx = stageSteps.indexOf(app.stage);
  const progressPct = app.stage === "rejected" ? 0 : ((currentStepIdx + 1) / stageSteps.length) * 100;

  return (
    <div className="flex flex-col h-full">
      {/* Header */}
      <div className="p-5 border-b bg-muted/30">
        <div className="flex items-center gap-3 mb-3">
          <div className="h-12 w-12 rounded-full bg-primary/10 flex items-center justify-center text-lg font-semibold text-primary font-sans">
            {app.customerInitials}
          </div>
          <div className="flex-1">
            <h3 className="font-heading text-lg">{app.customerName}</h3>
            <div className="flex items-center gap-2 mt-0.5">
              <span className="text-xs font-mono text-muted-foreground">{app.id}</span>
              <Badge variant="outline" className={`text-[10px] font-sans font-semibold ${stageConfig[app.stage].color}`}>
                {stageConfig[app.stage].label}
              </Badge>
            </div>
          </div>
        </div>
        {/* Stage Progress */}
        <div className="space-y-1.5">
          <Progress value={progressPct} className="h-1.5" />
          <div className="flex justify-between text-[9px] text-muted-foreground font-sans">
            {stageSteps.map((s) => (
              <span key={s} className={s === app.stage ? "text-primary font-semibold" : ""}>
                {stageConfig[s as ApplicationStage].label}
              </span>
            ))}
          </div>
        </div>
      </div>

      {/* Tabs */}
      <Tabs value={activeTab} onValueChange={setActiveTab} className="flex-1">
        <TabsList className="w-full justify-start rounded-none border-b bg-transparent h-10 px-5">
          <TabsTrigger value="summary" className="text-xs font-sans">Summary</TabsTrigger>
          <TabsTrigger value="credit" className="text-xs font-sans">Credit Assessment</TabsTrigger>
          <TabsTrigger value="documents" className="text-xs font-sans">Documents</TabsTrigger>
          <TabsTrigger value="decision" className="text-xs font-sans">Decision</TabsTrigger>
        </TabsList>

        <div className="p-5 overflow-y-auto">
          <TabsContent value="summary" className="mt-0 space-y-4">
            <div className="grid grid-cols-2 gap-4">
              {[
                { icon: User, label: "Customer", value: app.customerName },
                { icon: Phone, label: "Phone", value: app.phone },
                { icon: Hash, label: "National ID", value: app.nationalId },
                { icon: FileText, label: "Product", value: app.product },
                { icon: Calendar, label: "Application Date", value: app.date },
                { icon: MapPin, label: "Channel", value: app.channel },
              ].map((field) => (
                <div key={field.label} className="flex items-start gap-2.5">
                  <field.icon className="h-4 w-4 text-muted-foreground mt-0.5 shrink-0" />
                  <div>
                    <p className="text-[10px] text-muted-foreground font-sans uppercase tracking-wider">{field.label}</p>
                    <p className="text-xs font-sans font-medium">{field.value}</p>
                  </div>
                </div>
              ))}
            </div>
            <div className="grid grid-cols-3 gap-3 mt-4">
              <Card className="p-3 text-center">
                <p className="text-[10px] text-muted-foreground font-sans mb-1">Loan Amount</p>
                <p className="text-sm font-mono font-bold">{formatKES(app.amount)}</p>
              </Card>
              <Card className="p-3 text-center">
                <p className="text-[10px] text-muted-foreground font-sans mb-1">Tenor</p>
                <p className="text-sm font-mono font-bold">{app.tenor} months</p>
              </Card>
              <Card className="p-3 text-center">
                <p className="text-[10px] text-muted-foreground font-sans mb-1">Purpose</p>
                <p className="text-sm font-sans font-semibold">{app.purpose}</p>
              </Card>
            </div>
          </TabsContent>

          <TabsContent value="credit" className="mt-0 space-y-4">
            <Card className="p-4">
              <div className="flex items-center justify-between mb-3">
                <div>
                  <p className="text-[10px] text-muted-foreground font-sans uppercase tracking-wider">AthenaScore™</p>
                  <p className="text-3xl font-mono font-bold mt-1">{app.compositeScore}</p>
                  <Badge variant="outline" className={`text-[10px] mt-1 ${
                    app.compositeScore > 750 ? "bg-success/15 text-success border-success/30" :
                    app.compositeScore > 680 ? "bg-info/15 text-info border-info/30" :
                    app.compositeScore > 600 ? "bg-warning/15 text-warning border-warning/30" :
                    "bg-destructive/15 text-destructive border-destructive/30"
                  }`}>
                    {app.compositeLabel}
                  </Badge>
                </div>
                <Brain className="h-10 w-10 text-accent" />
              </div>
              <Card className={`p-4 border-l-4 mt-4 ${app.aiRecommendation === "APPROVAL" ? "border-l-success bg-success/5" : "border-l-destructive bg-destructive/5"}`}>
                <div className="flex items-center gap-2">
                  <Brain className="h-5 w-5 text-accent" />
                  <p className="text-xs font-sans font-semibold">
                    {app.aiRecommendation === "APPROVAL" ? "AI recommends APPROVAL" : "AI recommends DECLINE"} — {app.aiConfidence}% confidence
                  </p>
                </div>
              </Card>
            </Card>
          </TabsContent>

          <TabsContent value="documents" className="mt-0">
            {app.documents.length === 0 ? (
              <div className="text-center py-8 text-xs text-muted-foreground font-sans">
                No documents available for this application.
              </div>
            ) : (
              <div className="space-y-3">
                {app.documents.map((doc) => (
                  <div key={doc.name} className="flex items-center justify-between p-3 border rounded-lg hover:bg-muted/30 transition-colors">
                    <div className="flex items-center gap-3">
                      {doc.status === "verified" ? (
                        <CheckCircle2 className="h-4 w-4 text-success" />
                      ) : doc.status === "pending" ? (
                        <Clock className="h-4 w-4 text-warning" />
                      ) : (
                        <XCircle className="h-4 w-4 text-destructive" />
                      )}
                      <div>
                        <p className="text-xs font-sans font-medium">{doc.name}</p>
                        <p className="text-[10px] text-muted-foreground font-sans capitalize">{doc.status}</p>
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </TabsContent>

          <TabsContent value="decision" className="mt-0 space-y-4">
            <div className="grid grid-cols-3 gap-3">
              <Button className="bg-success hover:bg-success/90 text-white font-sans text-xs h-10">
                <CheckCircle2 className="h-4 w-4 mr-1.5" /> Approve
              </Button>
              <Button variant="destructive" className="font-sans text-xs h-10">
                <XCircle className="h-4 w-4 mr-1.5" /> Decline
              </Button>
              <Button variant="outline" className="font-sans text-xs h-10">
                <AlertTriangle className="h-4 w-4 mr-1.5" /> Request Info
              </Button>
            </div>
            <Card className="p-4 space-y-3">
              <div>
                <label className="text-[10px] text-muted-foreground font-sans uppercase tracking-wider">Conditions Precedent</label>
                <Textarea placeholder="Enter any conditions..." className="mt-1 text-xs font-sans" rows={3} />
              </div>
              <Button className="w-full font-sans text-xs">Submit Decision</Button>
            </Card>
          </TabsContent>
        </div>
      </Tabs>
    </div>
  );
}

export default LoansPage;
