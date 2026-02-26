import { DashboardLayout } from "@/components/DashboardLayout";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Bell, MessageSquare, Mail, Smartphone, Plus, Info } from "lucide-react";

const templates = [
  { id: "TPL-001", name: "Loan Approved", channel: "SMS", trigger: "Loan status → Approved", status: "Active", lastEdited: "2026-02-10" },
  { id: "TPL-002", name: "Loan Approved", channel: "Email", trigger: "Loan status → Approved", status: "Active", lastEdited: "2026-02-10" },
  { id: "TPL-003", name: "Payment Reminder (3 days)", channel: "SMS", trigger: "3 days before due date", status: "Active", lastEdited: "2026-01-22" },
  { id: "TPL-004", name: "Payment Overdue", channel: "SMS", trigger: "1 day past due", status: "Active", lastEdited: "2026-01-22" },
  { id: "TPL-005", name: "Payment Overdue", channel: "Push", trigger: "1 day past due", status: "Active", lastEdited: "2026-01-22" },
  { id: "TPL-006", name: "KYC Verification Required", channel: "Email", trigger: "Account created", status: "Draft", lastEdited: "2026-02-18" },
  { id: "TPL-007", name: "Float Limit Increased", channel: "SMS", trigger: "Float limit change", status: "Active", lastEdited: "2026-02-05" },
  { id: "TPL-008", name: "Monthly Statement", channel: "Email", trigger: "1st of month", status: "Paused", lastEdited: "2025-12-01" },
];

const channelIcon: Record<string, React.ReactNode> = {
  SMS: <MessageSquare className="h-3.5 w-3.5" />,
  Email: <Mail className="h-3.5 w-3.5" />,
  Push: <Smartphone className="h-3.5 w-3.5" />,
};

const statusStyle: Record<string, string> = {
  Active: "bg-success/10 text-success border-success/20",
  Draft: "bg-muted text-muted-foreground border-border",
  Paused: "bg-warning/10 text-warning border-warning/20",
};

const NotificationsPage = () => {
  return (
    <DashboardLayout
      title="Notification Templates"
      subtitle="SMS, email, and push notification templates"
      breadcrumbs={[{ label: "Home", href: "/" }, { label: "Administration" }, { label: "Notification Templates" }]}
    >
      <div className="space-y-6 animate-fade-in">
        {/* Info banner */}
        <div className="flex items-start gap-3 rounded-lg border border-border bg-muted/40 px-4 py-3">
          <Info className="h-4 w-4 text-muted-foreground mt-0.5 shrink-0" />
          <p className="text-xs text-muted-foreground">
            Notification templates — email/SMS channel configuration. Templates are dispatched by notification-service
            when loan lifecycle events occur.
          </p>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
          <Card>
            <CardContent className="p-5">
              <div className="flex items-center justify-between mb-2">
                <span className="text-xs text-muted-foreground font-sans">Total Templates</span>
                <Bell className="h-4 w-4 text-muted-foreground" />
              </div>
              <p className="text-2xl font-heading">{templates.length}</p>
            </CardContent>
          </Card>
          <Card>
            <CardContent className="p-5">
              <div className="flex items-center justify-between mb-2">
                <span className="text-xs text-muted-foreground font-sans">Active</span>
                <Bell className="h-4 w-4 text-success" />
              </div>
              <p className="text-2xl font-heading">{templates.filter((t) => t.status === "Active").length}</p>
            </CardContent>
          </Card>
          <Card>
            <CardContent className="p-5">
              <div className="flex items-center justify-between mb-2">
                <span className="text-xs text-muted-foreground font-sans">Channels</span>
                <MessageSquare className="h-4 w-4 text-muted-foreground" />
              </div>
              <p className="text-2xl font-heading">3</p>
              <p className="text-[10px] text-muted-foreground">SMS, Email, Push</p>
            </CardContent>
          </Card>
        </div>

        <Card>
          <CardHeader className="pb-3">
            <div className="flex items-center justify-between">
              <CardTitle className="text-sm font-medium">All Templates</CardTitle>
              <Button size="sm" className="text-xs gap-1.5"><Plus className="h-3.5 w-3.5" /> New Template</Button>
            </div>
          </CardHeader>
          <CardContent>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="text-xs">ID</TableHead>
                  <TableHead className="text-xs">Template Name</TableHead>
                  <TableHead className="text-xs">Channel</TableHead>
                  <TableHead className="text-xs">Trigger</TableHead>
                  <TableHead className="text-xs">Last Edited</TableHead>
                  <TableHead className="text-xs">Status</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {templates.map((t) => (
                  <TableRow key={t.id} className="table-row-hover cursor-pointer">
                    <TableCell className="text-xs font-mono">{t.id}</TableCell>
                    <TableCell className="text-sm font-medium">{t.name}</TableCell>
                    <TableCell>
                      <div className="flex items-center gap-1.5 text-xs">
                        {channelIcon[t.channel]}
                        {t.channel}
                      </div>
                    </TableCell>
                    <TableCell className="text-xs text-muted-foreground">{t.trigger}</TableCell>
                    <TableCell className="text-xs font-mono">{t.lastEdited}</TableCell>
                    <TableCell>
                      <Badge variant="outline" className={`text-[10px] ${statusStyle[t.status] || ""}`}>
                        {t.status}
                      </Badge>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </CardContent>
        </Card>
      </div>
    </DashboardLayout>
  );
};

export default NotificationsPage;
