import { useEffect, useState } from "react";
import { DashboardLayout } from "@/components/DashboardLayout";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Bell, MessageSquare, Mail, CheckCircle2, XCircle, MinusCircle, ChevronLeft, ChevronRight } from "lucide-react";
import { notificationService, NotificationLog } from "@/services/notificationService";

const statusBadge = (status: string) => {
  if (status === "SENT")
    return <Badge variant="outline" className="text-[10px] bg-success/10 text-success border-success/20 gap-1"><CheckCircle2 className="h-3 w-3" />{status}</Badge>;
  if (status === "FAILED")
    return <Badge variant="outline" className="text-[10px] bg-destructive/10 text-destructive border-destructive/20 gap-1"><XCircle className="h-3 w-3" />{status}</Badge>;
  return <Badge variant="outline" className="text-[10px] text-muted-foreground gap-1"><MinusCircle className="h-3 w-3" />{status}</Badge>;
};

const NotificationsPage = () => {
  const [logs, setLogs] = useState<NotificationLog[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    setLoading(true);
    notificationService.getLogs(page, 20).then((res) => {
      setLogs(res.content ?? []);
      setTotalPages(res.totalPages ?? 0);
      setTotalElements(res.totalElements ?? 0);
    }).catch(() => {
      setLogs([]);
    }).finally(() => setLoading(false));
  }, [page]);

  const sentCount = logs.filter((l) => l.status === "SENT").length;
  const failedCount = logs.filter((l) => l.status === "FAILED").length;

  return (
    <DashboardLayout
      title="Notification Logs"
      subtitle="Audit trail of all outbound notifications dispatched by LMS services"
      breadcrumbs={[{ label: "Home", href: "/" }, { label: "Administration" }, { label: "Notifications" }]}
    >
      <div className="space-y-6 animate-fade-in">
        {/* Summary cards */}
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
          <Card>
            <CardContent className="p-5">
              <div className="flex items-center justify-between mb-2">
                <span className="text-xs text-muted-foreground font-sans">Total (this page)</span>
                <Bell className="h-4 w-4 text-muted-foreground" />
              </div>
              <p className="text-2xl font-heading">{totalElements}</p>
            </CardContent>
          </Card>
          <Card>
            <CardContent className="p-5">
              <div className="flex items-center justify-between mb-2">
                <span className="text-xs text-muted-foreground font-sans">Sent</span>
                <CheckCircle2 className="h-4 w-4 text-success" />
              </div>
              <p className="text-2xl font-heading text-success">{sentCount}</p>
            </CardContent>
          </Card>
          <Card>
            <CardContent className="p-5">
              <div className="flex items-center justify-between mb-2">
                <span className="text-xs text-muted-foreground font-sans">Failed / Skipped</span>
                <XCircle className="h-4 w-4 text-destructive" />
              </div>
              <p className="text-2xl font-heading text-destructive">{failedCount}</p>
              <p className="text-[10px] text-muted-foreground">Skipped = channel disabled</p>
            </CardContent>
          </Card>
        </div>

        {/* Logs table */}
        <Card>
          <CardHeader className="pb-3">
            <div className="flex items-center justify-between">
              <CardTitle className="text-sm font-medium">
                Notification Log
                {totalElements > 0 && <span className="ml-2 text-xs text-muted-foreground font-normal">{totalElements} total</span>}
              </CardTitle>
              <div className="flex items-center gap-1 text-xs text-muted-foreground">
                <MessageSquare className="h-3.5 w-3.5 mr-1" />
                Live data from notification-service
              </div>
            </div>
          </CardHeader>
          <CardContent>
            {loading ? (
              <p className="text-xs text-muted-foreground py-4 text-center">Loading logs…</p>
            ) : logs.length === 0 ? (
              <p className="text-xs text-muted-foreground py-4 text-center">
                No notification logs yet. Logs appear when LMS lifecycle events trigger notifications
                (loan submitted, disbursed, repayment, KYC).
              </p>
            ) : (
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead className="text-xs">When</TableHead>
                    <TableHead className="text-xs">Service</TableHead>
                    <TableHead className="text-xs">Type</TableHead>
                    <TableHead className="text-xs">Recipient</TableHead>
                    <TableHead className="text-xs">Subject</TableHead>
                    <TableHead className="text-xs">Status</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {logs.map((log) => (
                    <TableRow key={log.id} className="table-row-hover">
                      <TableCell className="text-xs font-mono whitespace-nowrap">
                        {new Date(log.sentAt).toLocaleString()}
                      </TableCell>
                      <TableCell className="text-xs">{log.serviceName}</TableCell>
                      <TableCell>
                        <div className="flex items-center gap-1.5 text-xs">
                          {log.type === "EMAIL" ? <Mail className="h-3.5 w-3.5" /> : <MessageSquare className="h-3.5 w-3.5" />}
                          {log.type}
                        </div>
                      </TableCell>
                      <TableCell className="text-xs max-w-[160px] truncate">{log.recipient}</TableCell>
                      <TableCell className="text-xs max-w-[200px] truncate text-muted-foreground">
                        {log.subject ?? "—"}
                      </TableCell>
                      <TableCell>{statusBadge(log.status)}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            )}

            {/* Pagination */}
            {totalPages > 1 && (
              <div className="flex items-center justify-between mt-4 pt-3 border-t border-border">
                <p className="text-xs text-muted-foreground">Page {page + 1} of {totalPages}</p>
                <div className="flex gap-2">
                  <Button size="sm" variant="outline" className="text-xs" onClick={() => setPage((p) => p - 1)} disabled={page === 0}>
                    <ChevronLeft className="h-3.5 w-3.5 mr-1" /> Prev
                  </Button>
                  <Button size="sm" variant="outline" className="text-xs" onClick={() => setPage((p) => p + 1)} disabled={page >= totalPages - 1}>
                    Next <ChevronRight className="h-3.5 w-3.5 ml-1" />
                  </Button>
                </div>
              </div>
            )}
          </CardContent>
        </Card>
      </div>
    </DashboardLayout>
  );
};

export default NotificationsPage;
