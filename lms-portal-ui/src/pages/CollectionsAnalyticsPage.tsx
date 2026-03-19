import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { SidebarProvider } from "@/components/ui/sidebar";
import { AppSidebar } from "@/components/AppSidebar";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import {
  PieChart,
  Pie,
  Cell,
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  Legend,
} from "recharts";
import {
  collectionsService,
  type DashboardAnalytics,
  type OfficerPerformance,
  type AgeingBucket,
} from "@/services/collectionsService";

const STAGE_COLORS: Record<string, string> = {
  WATCH: "#EAB308",
  SUBSTANDARD: "#F97316",
  DOUBTFUL: "#EF4444",
  LOSS: "#991B1B",
};

const BUCKET_COLORS: Record<string, string> = {
  "1-30": "#3B82F6",
  "31-60": "#F59E0B",
  "61-90": "#EF4444",
  "90+": "#7C3AED",
};

function formatCurrency(n: number): string {
  return new Intl.NumberFormat("en-US", {
    style: "currency",
    currency: "USD",
    minimumFractionDigits: 0,
    maximumFractionDigits: 0,
  }).format(n);
}

function formatPct(n: number): string {
  return `${n.toFixed(1)}%`;
}

function defaultFrom(): string {
  const d = new Date();
  d.setDate(d.getDate() - 30);
  return d.toISOString().slice(0, 10);
}

function defaultTo(): string {
  return new Date().toISOString().slice(0, 10);
}

export default function CollectionsAnalyticsPage() {
  const [from, setFrom] = useState(defaultFrom);
  const [to, setTo] = useState(defaultTo);

  const dashQ = useQuery<DashboardAnalytics>({
    queryKey: ["collections-analytics-dashboard", from, to],
    queryFn: () => collectionsService.getDashboardAnalytics(from, to),
  });

  const officerQ = useQuery<OfficerPerformance[]>({
    queryKey: ["collections-analytics-officers", from, to],
    queryFn: () => collectionsService.getOfficerPerformance(from, to),
  });

  const ageingQ = useQuery<AgeingBucket[]>({
    queryKey: ["collections-analytics-ageing"],
    queryFn: () => collectionsService.getAgeingReport(),
  });

  const dash = dashQ.data;
  const officers = officerQ.data ?? [];
  const ageing = ageingQ.data ?? [];

  // Merge ageing by bucket (collapse product_type)
  const ageingMerged = ageing.reduce<Record<string, { bucket: string; count: number; amount: number }>>((acc, b) => {
    if (!acc[b.bucket]) acc[b.bucket] = { bucket: b.bucket, count: 0, amount: 0 };
    acc[b.bucket].count += b.count;
    acc[b.bucket].amount += b.amount;
    return acc;
  }, {});
  const ageingData = Object.values(ageingMerged).sort((a, b) => {
    const order = ["1-30", "31-60", "61-90", "90+"];
    return order.indexOf(a.bucket) - order.indexOf(b.bucket);
  });

  const stageData = (dash?.ageingByStage ?? []).map((s) => ({
    name: s.stage,
    value: s.count,
    amount: s.amount,
  }));

  return (
    <SidebarProvider>
      <div className="flex min-h-screen w-full">
        <AppSidebar />
        <main className="flex-1 p-6 space-y-6 overflow-auto">
          <div className="flex items-center justify-between">
            <h1 className="text-2xl font-bold">Collections Analytics</h1>
            <div className="flex items-center gap-4">
              <div className="flex items-center gap-2">
                <Label htmlFor="from" className="text-sm whitespace-nowrap">From</Label>
                <Input
                  id="from"
                  type="date"
                  value={from}
                  onChange={(e) => setFrom(e.target.value)}
                  className="w-40"
                />
              </div>
              <div className="flex items-center gap-2">
                <Label htmlFor="to" className="text-sm whitespace-nowrap">To</Label>
                <Input
                  id="to"
                  type="date"
                  value={to}
                  onChange={(e) => setTo(e.target.value)}
                  className="w-40"
                />
              </div>
            </div>
          </div>

          {/* KPI Row */}
          <div className="grid grid-cols-2 md:grid-cols-4 lg:grid-cols-7 gap-4">
            <KpiCard title="Recovery Rate" value={dash ? formatPct(dash.recoveryRate) : "--"} />
            <KpiCard title="Total Recovered" value={dash ? formatCurrency(dash.totalRecovered) : "--"} />
            <KpiCard title="Total Outstanding" value={dash ? formatCurrency(dash.totalOutstanding) : "--"} />
            <KpiCard title="New Cases" value={dash?.newCases?.toString() ?? "--"} />
            <KpiCard title="Closed Cases" value={dash?.closedCases?.toString() ?? "--"} />
            <KpiCard title="Avg DPD" value={dash ? dash.avgDPD.toFixed(0) : "--"} />
            <KpiCard title="PTP Fulfilment" value={dash ? formatPct(dash.ptpFulfilmentRate) : "--"} />
          </div>

          {/* Charts Row */}
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
            {/* Stage Distribution */}
            <Card>
              <CardHeader>
                <CardTitle className="text-base">Stage Distribution</CardTitle>
              </CardHeader>
              <CardContent>
                {stageData.length > 0 ? (
                  <ResponsiveContainer width="100%" height={280}>
                    <PieChart>
                      <Pie
                        data={stageData}
                        dataKey="value"
                        nameKey="name"
                        cx="50%"
                        cy="50%"
                        innerRadius={50}
                        outerRadius={100}
                        label={({ name, value }) => `${name}: ${value}`}
                      >
                        {stageData.map((entry) => (
                          <Cell key={entry.name} fill={STAGE_COLORS[entry.name] ?? "#94A3B8"} />
                        ))}
                      </Pie>
                      <Tooltip formatter={(value: number, name: string) => [`${value} cases`, name]} />
                      <Legend />
                    </PieChart>
                  </ResponsiveContainer>
                ) : (
                  <p className="text-muted-foreground text-sm text-center py-12">No data available</p>
                )}
              </CardContent>
            </Card>

            {/* Ageing Pyramid */}
            <Card>
              <CardHeader>
                <CardTitle className="text-base">DPD Ageing Buckets</CardTitle>
              </CardHeader>
              <CardContent>
                {ageingData.length > 0 ? (
                  <ResponsiveContainer width="100%" height={280}>
                    <BarChart data={ageingData}>
                      <CartesianGrid strokeDasharray="3 3" />
                      <XAxis dataKey="bucket" />
                      <YAxis yAxisId="count" orientation="left" />
                      <YAxis yAxisId="amount" orientation="right" tickFormatter={(v) => `$${(v / 1000).toFixed(0)}k`} />
                      <Tooltip
                        formatter={(value: number, name: string) =>
                          name === "amount" ? [formatCurrency(value), "Amount"] : [value, "Cases"]
                        }
                      />
                      <Bar yAxisId="count" dataKey="count" name="Cases" radius={[4, 4, 0, 0]}>
                        {ageingData.map((entry) => (
                          <Cell key={entry.bucket} fill={BUCKET_COLORS[entry.bucket] ?? "#64748B"} />
                        ))}
                      </Bar>
                      <Bar yAxisId="amount" dataKey="amount" name="Amount" fill="#94A3B8" opacity={0.5} radius={[4, 4, 0, 0]} />
                      <Legend />
                    </BarChart>
                  </ResponsiveContainer>
                ) : (
                  <p className="text-muted-foreground text-sm text-center py-12">No data available</p>
                )}
              </CardContent>
            </Card>
          </div>

          {/* Officer Leaderboard */}
          <Card>
            <CardHeader>
              <CardTitle className="text-base">Officer Leaderboard</CardTitle>
            </CardHeader>
            <CardContent>
              {officers.length > 0 ? (
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Officer</TableHead>
                      <TableHead className="text-right">Active Cases</TableHead>
                      <TableHead className="text-right">Actions</TableHead>
                      <TableHead className="text-right">PTPs Created</TableHead>
                      <TableHead className="text-right">PTPs Fulfilled</TableHead>
                      <TableHead className="text-right">Cases Closed</TableHead>
                      <TableHead className="text-right">Avg Resolution (days)</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {officers.map((o) => (
                      <TableRow key={o.username}>
                        <TableCell className="font-medium">{o.username}</TableCell>
                        <TableCell className="text-right">{o.activeCases}</TableCell>
                        <TableCell className="text-right">{o.actionsCount}</TableCell>
                        <TableCell className="text-right">{o.ptpsCreated}</TableCell>
                        <TableCell className="text-right">{o.ptpsFulfilled}</TableCell>
                        <TableCell className="text-right">{o.casesClosed}</TableCell>
                        <TableCell className="text-right">{o.avgResolutionDays.toFixed(1)}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              ) : (
                <p className="text-muted-foreground text-sm text-center py-8">No officer data available</p>
              )}
            </CardContent>
          </Card>
        </main>
      </div>
    </SidebarProvider>
  );
}

function KpiCard({ title, value }: { title: string; value: string }) {
  return (
    <Card>
      <CardContent className="pt-4 pb-3 px-4">
        <p className="text-xs text-muted-foreground mb-1">{title}</p>
        <p className="text-lg font-semibold">{value}</p>
      </CardContent>
    </Card>
  );
}
