import { useQuery } from "@tanstack/react-query";
import { DashboardLayout } from "@/components/DashboardLayout";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Loader2, Globe, Clock, Info } from "lucide-react";
import { orgService } from "@/services/orgService";

const COUNTRY_NAMES: Record<string, { name: string; region: string }> = {
  KEN: { name: "Kenya", region: "East Africa" },
  UGA: { name: "Uganda", region: "East Africa" },
  TZA: { name: "Tanzania", region: "East Africa" },
  GHA: { name: "Ghana", region: "West Africa" },
  NGA: { name: "Nigeria", region: "West Africa" },
};

const CountryConfigPage = () => {
  const { data: settings, isLoading, isError } = useQuery({
    queryKey: ["org", "settings"],
    queryFn: () => orgService.getSettings(),
  });

  const countryCode = settings?.countryCode ?? "KEN";
  const countryInfo = COUNTRY_NAMES[countryCode] ?? { name: countryCode, region: "Africa" };

  return (
    <DashboardLayout
      title="Country Configuration"
      subtitle="Country, region, and timezone settings for this organization"
    >
      {isLoading && (
        <div className="flex items-center justify-center h-64 text-muted-foreground">
          <Loader2 className="h-6 w-6 animate-spin mr-2" />
          <span>Loading country configuration...</span>
        </div>
      )}

      {isError && (
        <div className="flex items-center justify-center h-64 text-destructive">
          <p>Failed to load country configuration. Please try again.</p>
        </div>
      )}

      {settings && (
        <div className="space-y-6">
          {/* Country Card */}
          <Card>
            <CardHeader>
              <CardTitle className="text-base">Operating Country</CardTitle>
            </CardHeader>
            <CardContent>
              <div className="flex items-center justify-between p-4 border rounded-lg bg-muted/30">
                <div className="flex items-center gap-4">
                  <div className="flex items-center justify-center w-10 h-10 rounded-full bg-primary/10 text-primary">
                    <Globe className="h-5 w-5" />
                  </div>
                  <div>
                    <p className="font-semibold text-sm">{countryInfo.name}</p>
                    <p className="text-xs text-muted-foreground">
                      {countryInfo.region} &mdash; ISO 3166-1: {countryCode}
                    </p>
                  </div>
                </div>
                <Badge variant="default">Active</Badge>
              </div>
            </CardContent>
          </Card>

          {/* Timezone Card */}
          <Card>
            <CardHeader>
              <CardTitle className="text-base">Timezone</CardTitle>
            </CardHeader>
            <CardContent>
              <div className="flex items-center gap-4 p-4 border rounded-lg bg-muted/30">
                <div className="flex items-center justify-center w-10 h-10 rounded-full bg-primary/10 text-primary">
                  <Clock className="h-5 w-5" />
                </div>
                <div>
                  <p className="font-semibold text-sm">{settings.timezone}</p>
                  <p className="text-xs text-muted-foreground">
                    All transaction timestamps use this timezone
                  </p>
                </div>
              </div>
            </CardContent>
          </Card>

          {/* Note */}
          <div className="flex items-start gap-2 p-3 bg-blue-50 border border-blue-200 rounded-lg text-sm text-blue-800">
            <Info className="h-4 w-4 mt-0.5 shrink-0" />
            <span>
              Single-country configuration. This organization operates in{" "}
              <strong>{countryInfo.name}</strong>. Multi-country support requires a separate
              configuration.
            </span>
          </div>
        </div>
      )}
    </DashboardLayout>
  );
};

export default CountryConfigPage;
