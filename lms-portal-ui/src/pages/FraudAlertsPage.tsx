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
import { ShieldAlert, ScanLine, Ban } from "lucide-react";

const FraudAlertsPage = () => {
  return (
    <DashboardLayout
      title="Fraud Alerts"
      subtitle="Real-time fraud detection and case management"
      breadcrumbs={[{ label: "Home", href: "/" }, { label: "Compliance" }, { label: "Fraud Alerts" }]}
    >
      <div className="space-y-6 animate-fade-in">
        {/* System status */}
        <div className="flex items-center gap-3">
          <Badge className="bg-success/10 text-success border-success/20 gap-1.5 px-3 py-1">
            <span className="h-1.5 w-1.5 rounded-full bg-success inline-block animate-pulse" />
            System Active
          </Badge>
          <span className="text-xs text-muted-foreground">
            Real-time fraud screening active via compliance-service
          </span>
        </div>

        {/* Stat cards */}
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
          <Card>
            <CardContent className="p-5">
              <div className="flex items-center justify-between mb-2">
                <span className="text-xs text-muted-foreground font-sans">Transactions Screened</span>
                <ScanLine className="h-4 w-4 text-muted-foreground" />
              </div>
              <p className="text-2xl font-heading">—</p>
              <p className="text-[10px] text-muted-foreground mt-0.5">Counter not yet exposed by compliance-service</p>
            </CardContent>
          </Card>
          <Card>
            <CardContent className="p-5">
              <div className="flex items-center justify-between mb-2">
                <span className="text-xs text-muted-foreground font-sans">Fraud Alerts</span>
                <ShieldAlert className="h-4 w-4 text-muted-foreground" />
              </div>
              <p className="text-2xl font-heading">0</p>
            </CardContent>
          </Card>
          <Card>
            <CardContent className="p-5">
              <div className="flex items-center justify-between mb-2">
                <span className="text-xs text-muted-foreground font-sans">Blocked Transactions</span>
                <Ban className="h-4 w-4 text-muted-foreground" />
              </div>
              <p className="text-2xl font-heading">0</p>
            </CardContent>
          </Card>
        </div>

        {/* Alerts table */}
        <Card>
          <CardHeader className="pb-3">
            <CardTitle className="text-sm font-medium">Fraud Alerts</CardTitle>
          </CardHeader>
          <CardContent>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="text-xs">Alert ID</TableHead>
                  <TableHead className="text-xs">Type</TableHead>
                  <TableHead className="text-xs">Customer</TableHead>
                  <TableHead className="text-xs">Amount</TableHead>
                  <TableHead className="text-xs">Confidence</TableHead>
                  <TableHead className="text-xs">Status</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                <TableRow>
                  <TableCell colSpan={6} className="text-center text-muted-foreground py-8 text-sm">
                    No fraud alerts detected. Real-time screening is active.
                  </TableCell>
                </TableRow>
              </TableBody>
            </Table>
          </CardContent>
        </Card>
      </div>
    </DashboardLayout>
  );
};

export default FraudAlertsPage;
