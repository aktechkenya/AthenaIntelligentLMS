import { DashboardLayout } from "@/components/DashboardLayout";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Scale, FileText, AlertTriangle, Banknote } from "lucide-react";

const LegalPage = () => {
  return (
    <DashboardLayout
      title="Legal & Write-Offs"
      subtitle="Legal recovery and write-off management"
      breadcrumbs={[{ label: "Home", href: "/" }, { label: "Collections" }, { label: "Legal & Write-Offs" }]}
    >
      <div className="space-y-6 animate-fade-in">
        <div className="grid grid-cols-1 sm:grid-cols-4 gap-4">
          <Card>
            <CardContent className="p-5">
              <div className="flex items-center justify-between mb-2">
                <span className="text-xs text-muted-foreground font-sans">Total Cases</span>
                <Scale className="h-4 w-4 text-muted-foreground" />
              </div>
              <p className="text-2xl font-heading">0</p>
            </CardContent>
          </Card>
          <Card>
            <CardContent className="p-5">
              <div className="flex items-center justify-between mb-2">
                <span className="text-xs text-muted-foreground font-sans">Active Cases</span>
                <FileText className="h-4 w-4 text-warning" />
              </div>
              <p className="text-2xl font-heading">0</p>
            </CardContent>
          </Card>
          <Card>
            <CardContent className="p-5">
              <div className="flex items-center justify-between mb-2">
                <span className="text-xs text-muted-foreground font-sans">Total Exposure</span>
                <Banknote className="h-4 w-4 text-destructive" />
              </div>
              <p className="text-2xl font-heading">KES 0</p>
            </CardContent>
          </Card>
          <Card>
            <CardContent className="p-5">
              <div className="flex items-center justify-between mb-2">
                <span className="text-xs text-muted-foreground font-sans">Avg DPD</span>
                <AlertTriangle className="h-4 w-4 text-warning" />
              </div>
              <p className="text-2xl font-heading">0</p>
            </CardContent>
          </Card>
        </div>

        <Card>
          <CardHeader className="pb-3">
            <CardTitle className="text-sm font-medium">Legal Cases</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="flex flex-col items-center justify-center h-48 text-muted-foreground">
              <Scale className="h-8 w-8 mb-2 text-muted-foreground/50" />
              <p className="text-sm font-medium">No legal cases</p>
              <p className="text-xs mt-1">Legal cases will appear here when loans are escalated for recovery.</p>
              <p className="text-[10px] mt-3 text-muted-foreground/70">
                Loans with DPD &gt; 90 in the Collections Workbench can be escalated to legal recovery.
              </p>
            </div>
          </CardContent>
        </Card>
      </div>
    </DashboardLayout>
  );
};

export default LegalPage;
