import { useQuery } from "@tanstack/react-query";
import { DashboardLayout } from "@/components/DashboardLayout";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Loader2, Lock, Info } from "lucide-react";
import { orgService } from "@/services/orgService";

const CurrenciesFxPage = () => {
  const { data: settings, isLoading, isError } = useQuery({
    queryKey: ["org", "settings"],
    queryFn: () => orgService.getSettings(),
  });

  return (
    <DashboardLayout
      title="Currencies & FX"
      subtitle="Base currency and foreign exchange configuration"
    >
      {isLoading && (
        <div className="flex items-center justify-center h-64 text-muted-foreground">
          <Loader2 className="h-6 w-6 animate-spin mr-2" />
          <span>Loading currency settings...</span>
        </div>
      )}

      {isError && (
        <div className="flex items-center justify-center h-64 text-destructive">
          <p>Failed to load currency settings. Please try again.</p>
        </div>
      )}

      {settings && (
        <div className="space-y-6">
          {/* Base Currency Card */}
          <Card>
            <CardHeader>
              <CardTitle className="text-base">Base Currency</CardTitle>
            </CardHeader>
            <CardContent>
              <div className="flex items-center justify-between p-4 border rounded-lg bg-muted/30">
                <div className="flex items-center gap-4">
                  <div className="flex items-center justify-center w-10 h-10 rounded-full bg-primary/10 text-primary font-bold text-sm">
                    {settings.currency}
                  </div>
                  <div>
                    <p className="font-semibold text-sm">
                      {settings.currency === "KES" ? "Kenyan Shilling" : settings.currency}
                    </p>
                    <p className="text-xs text-muted-foreground">
                      ISO 4217 code: {settings.currency}
                    </p>
                  </div>
                </div>
                <div className="flex items-center gap-3">
                  <Badge variant="default">Active</Badge>
                  <Lock className="h-4 w-4 text-muted-foreground" />
                </div>
              </div>
              <div className="flex items-start gap-2 mt-4 p-3 bg-amber-50 border border-amber-200 rounded-lg text-sm text-amber-800">
                <Info className="h-4 w-4 mt-0.5 shrink-0" />
                <span>
                  Single currency per organization. Contact support to change the base currency.
                </span>
              </div>
            </CardContent>
          </Card>

          {/* Exchange Rates Card */}
          <Card>
            <CardHeader>
              <CardTitle className="text-base">Exchange Rates</CardTitle>
            </CardHeader>
            <CardContent>
              <div className="flex items-start gap-2 p-4 border rounded-lg text-sm text-muted-foreground">
                <Info className="h-4 w-4 mt-0.5 shrink-0 text-blue-500" />
                <span>
                  Multi-currency is not supported in this configuration. All transactions are
                  processed in <strong>{settings.currency}</strong>. No exchange rates are
                  applicable.
                </span>
              </div>
            </CardContent>
          </Card>
        </div>
      )}
    </DashboardLayout>
  );
};

export default CurrenciesFxPage;
