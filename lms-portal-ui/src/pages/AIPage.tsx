import { useMemo } from "react";
import { DashboardLayout } from "@/components/DashboardLayout";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from "@/components/ui/table";
import { Brain, BarChart3, TrendingUp, Target } from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { scoringService, type ScoringRequest } from "@/services/scoringService";

const bandColor: Record<string, string> = {
  A: "bg-success/15 text-success border-success/30",
  B: "bg-info/15 text-info border-info/30",
  C: "bg-warning/15 text-warning border-warning/30",
  D: "bg-destructive/15 text-destructive border-destructive/30",
};

const AIPage = () => {
  const { data: apiPage, isLoading } = useQuery({
    queryKey: ["scoring-requests"],
    queryFn: () => scoringService.listRequests(0, 100),
    staleTime: 60_000,
    retry: false,
  });

  const requests: ScoringRequest[] = apiPage?.content ?? [];

  const stats = useMemo(() => {
    if (requests.length === 0) return { total: 0, completed: 0, failed: 0, avgScore: 0, bands: {} as Record<string, number> };
    const completed = requests.filter(r => r.status === "COMPLETED").length;
    const failed = requests.filter(r => r.status === "FAILED" || r.errorMessage).length;
    const scored = requests.filter(r => r.score != null);
    const avgScore = scored.length > 0 ? Math.round(scored.reduce((s, r) => s + (r.score ?? 0), 0) / scored.length) : 0;
    const bands: Record<string, number> = {};
    scored.forEach(r => {
      const b = r.scoreBand ?? "?";
      bands[b] = (bands[b] || 0) + 1;
    });
    return { total: requests.length, completed, failed, avgScore, bands };
  }, [requests]);

  return (
    <DashboardLayout
      title="AI Intelligence"
      subtitle="Credit scoring requests and model performance"
      breadcrumbs={[{ label: "Home", href: "/" }, { label: "AI Intelligence" }]}
    >
      <div className="space-y-4 animate-fade-in">
        {/* KPI Cards */}
        {isLoading ? (
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
            {Array.from({ length: 4 }).map((_, i) => <Skeleton key={i} className="h-24 w-full" />)}
          </div>
        ) : (
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
            <Card>
              <CardContent className="p-5">
                <div className="flex items-center justify-between mb-2">
                  <span className="text-xs text-muted-foreground font-sans">Total Requests</span>
                  <Brain className="h-4 w-4 text-accent" />
                </div>
                <p className="text-2xl font-heading">{stats.total}</p>
              </CardContent>
            </Card>
            <Card>
              <CardContent className="p-5">
                <div className="flex items-center justify-between mb-2">
                  <span className="text-xs text-muted-foreground font-sans">Completed</span>
                  <BarChart3 className="h-4 w-4 text-success" />
                </div>
                <p className="text-2xl font-heading">{stats.completed}</p>
              </CardContent>
            </Card>
            <Card>
              <CardContent className="p-5">
                <div className="flex items-center justify-between mb-2">
                  <span className="text-xs text-muted-foreground font-sans">Failed</span>
                  <Target className="h-4 w-4 text-destructive" />
                </div>
                <p className="text-2xl font-heading">{stats.failed}</p>
              </CardContent>
            </Card>
            <Card>
              <CardContent className="p-5">
                <div className="flex items-center justify-between mb-2">
                  <span className="text-xs text-muted-foreground font-sans">Success Rate</span>
                  <TrendingUp className="h-4 w-4 text-info" />
                </div>
                <p className="text-2xl font-heading">{stats.total > 0 ? Math.round((stats.completed / stats.total) * 100) : 0}%</p>
              </CardContent>
            </Card>
          </div>
        )}

        {/* Band Distribution (shown when score data available) */}
        {!isLoading && Object.keys(stats.bands).filter(k => k !== "?").length > 0 && (
          <div className="flex items-center gap-3">
            <span className="text-xs text-muted-foreground font-sans">Band Distribution:</span>
            {Object.entries(stats.bands).filter(([k]) => k !== "?").sort(([a], [b]) => a.localeCompare(b)).map(([band, count]) => (
              <Badge key={band} variant="outline" className={`text-xs font-mono ${bandColor[band] ?? ""}`}>
                {band}: {count}
              </Badge>
            ))}
          </div>
        )}

        {/* Requests Table */}
        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="text-sm font-medium flex items-center gap-2">
              <Brain className="h-4 w-4" /> Scoring Requests
            </CardTitle>
          </CardHeader>
          <CardContent className="p-0">
            {isLoading ? (
              <div className="p-4 space-y-2">
                {Array.from({ length: 8 }).map((_, i) => <Skeleton key={i} className="h-10 w-full" />)}
              </div>
            ) : requests.length === 0 ? (
              <div className="flex flex-col items-center justify-center h-48 text-muted-foreground">
                <Brain className="h-8 w-8 mb-2 text-muted-foreground/50" />
                <p className="text-sm font-medium">No scoring requests</p>
                <p className="text-xs mt-1">Credit scoring requests will appear here when loans are processed.</p>
              </div>
            ) : (
              <Table>
                <TableHeader>
                  <TableRow className="hover:bg-transparent">
                    <TableHead className="text-[10px] uppercase tracking-wider font-sans">Customer ID</TableHead>
                    <TableHead className="text-[10px] uppercase tracking-wider font-sans">Application</TableHead>
                    <TableHead className="text-[10px] uppercase tracking-wider font-sans">Trigger</TableHead>
                    <TableHead className="text-[10px] uppercase tracking-wider font-sans">Status</TableHead>
                    <TableHead className="text-[10px] uppercase tracking-wider font-sans">Requested</TableHead>
                    <TableHead className="text-[10px] uppercase tracking-wider font-sans">Completed</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {requests.map((req) => (
                    <TableRow key={req.id} className="table-row-hover">
                      <TableCell className="text-xs font-mono font-medium">{req.customerId}</TableCell>
                      <TableCell className="text-xs font-mono text-muted-foreground">{req.loanApplicationId?.slice(0, 8) ?? "—"}</TableCell>
                      <TableCell className="text-xs font-sans text-muted-foreground">{req.triggerEvent ?? "—"}</TableCell>
                      <TableCell>
                        <Badge variant="outline" className={`text-[9px] font-sans ${
                          req.status === "COMPLETED" ? "bg-success/15 text-success border-success/30" :
                          req.status === "FAILED" ? "bg-destructive/15 text-destructive border-destructive/30" :
                          "bg-warning/15 text-warning border-warning/30"
                        }`}>
                          {req.status ?? "PENDING"}
                        </Badge>
                      </TableCell>
                      <TableCell className="text-xs font-sans">{req.requestedAt?.split("T")[0] ?? "—"}</TableCell>
                      <TableCell className="text-xs font-sans">{req.completedAt?.replace("T", " ").slice(0, 19) ?? "—"}</TableCell>
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

export default AIPage;
