import { DashboardLayout } from "@/components/DashboardLayout";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Download, FileText, Calendar } from "lucide-react";

const savedReports = [
  { name: "Monthly Portfolio Summary", type: "PDF", schedule: "1st of month" },
  { name: "PAR Aging Report", type: "Excel", schedule: "Weekly - Monday" },
  { name: "IFRS 9 ECL Report", type: "PDF", schedule: "Quarterly" },
  { name: "Disbursement Report", type: "Excel", schedule: "Daily" },
  { name: "Collections Performance", type: "PDF", schedule: "Weekly - Friday" },
];

const ReportsPage = () => {
  return (
    <DashboardLayout title="Reports & Analytics" subtitle="Business intelligence & reporting">
      <div className="space-y-4 animate-fade-in">
        <Card>
          <CardHeader className="pb-2">
            <div className="flex items-center justify-between">
              <CardTitle className="text-sm font-medium">Saved Reports</CardTitle>
              <Button variant="outline" size="sm" className="text-xs">
                <FileText className="mr-1.5 h-3.5 w-3.5" /> New Report
              </Button>
            </div>
          </CardHeader>
          <CardContent>
            <div className="space-y-3">
              {savedReports.map((report, i) => (
                <div
                  key={i}
                  className="flex items-center justify-between py-2 border-b border-border/50 last:border-0"
                >
                  <div className="flex items-center gap-3">
                    <div className="h-8 w-8 rounded bg-primary/10 flex items-center justify-center">
                      <FileText className="h-4 w-4 text-primary" />
                    </div>
                    <div>
                      <p className="text-xs font-medium">{report.name}</p>
                      <div className="flex items-center gap-2 mt-0.5">
                        <span className="text-[10px] text-muted-foreground">{report.type}</span>
                        <span className="text-[10px] text-muted-foreground">·</span>
                        <div className="flex items-center gap-1">
                          <Calendar className="h-2.5 w-2.5 text-muted-foreground" />
                          <span className="text-[10px] text-muted-foreground">{report.schedule}</span>
                        </div>
                      </div>
                    </div>
                  </div>
                  <Button variant="ghost" size="sm" className="text-xs text-muted-foreground">
                    <Download className="h-3.5 w-3.5" />
                  </Button>
                </div>
              ))}
            </div>
          </CardContent>
        </Card>
      </div>
    </DashboardLayout>
  );
};

export default ReportsPage;
