import { useState, useEffect } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { DashboardLayout } from "@/components/DashboardLayout";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Separator } from "@/components/ui/separator";
import { Switch } from "@/components/ui/switch";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Building2, Shield, Bell, Globe, Lock, Loader2, CheckCircle2, XCircle, ExternalLink } from "lucide-react";
import { orgService, type UpdateOrgSettings } from "@/services/orgService";
import { apiGet } from "@/lib/api";
import { toast } from "@/hooks/use-toast";

// Services to health-check for the integrations tab
const serviceChecks = [
  { key: "auth", label: "Account Service", desc: "Users, accounts, org settings", proxy: "/proxy/auth" },
  { key: "products", label: "Product Catalog", desc: "Loan & savings products", proxy: "/proxy/products" },
  { key: "loans", label: "Loan Management", desc: "Loan lifecycle & servicing", proxy: "/proxy/loans" },
  { key: "payments", label: "Payment Service", desc: "Disbursements & collections", proxy: "/proxy/payments" },
  { key: "accounting", label: "Accounting / GL", desc: "General ledger & journals", proxy: "/proxy/accounting" },
  { key: "overdraft", label: "Overdraft Service", desc: "Overdraft products & limits", proxy: "/proxy/overdraft" },
  { key: "notifications", label: "Notification Service", desc: "Email & SMS delivery", proxy: "/proxy/notifications" },
  { key: "media", label: "Media Service", desc: "Document & file storage", proxy: "/proxy/media" },
  { key: "fraud", label: "Fraud Detection", desc: "Rules, alerts & risk scoring", proxy: "/proxy/fraud" },
];

interface HealthResult {
  key: string;
  status: "UP" | "DOWN" | "LOADING";
}

const SettingsPage = () => {
  const queryClient = useQueryClient();
  const [orgName, setOrgName] = useState("");

  // Security tab state
  const [twoFactorEnabled, setTwoFactorEnabled] = useState(false);
  const [sessionTimeoutEnabled, setSessionTimeoutEnabled] = useState(true);
  const [auditTrailEnabled, setAuditTrailEnabled] = useState(true);
  const [ipWhitelistEnabled, setIpWhitelistEnabled] = useState(false);
  const [securityDirty, setSecurityDirty] = useState(false);

  // Integrations health state
  const [healthResults, setHealthResults] = useState<HealthResult[]>(
    serviceChecks.map((s) => ({ key: s.key, status: "LOADING" as const }))
  );

  const { data: orgSettings, isLoading } = useQuery({
    queryKey: ["org", "settings"],
    queryFn: orgService.getSettings,
  });

  // Notification config queries
  const { data: emailConfig } = useQuery({
    queryKey: ["notification-config", "EMAIL"],
    queryFn: async () => {
      const result = await apiGet<{ channel: string; enabled?: boolean }>("/proxy/notifications/api/v1/notifications/config/EMAIL");
      return result.data;
    },
  });

  const { data: smsConfig } = useQuery({
    queryKey: ["notification-config", "SMS"],
    queryFn: async () => {
      const result = await apiGet<{ channel: string; enabled?: boolean }>("/proxy/notifications/api/v1/notifications/config/SMS");
      return result.data;
    },
  });

  // Populate controlled inputs once data arrives
  useEffect(() => {
    if (orgSettings) {
      setOrgName(orgSettings.orgName ?? "");
      setTwoFactorEnabled(orgSettings.twoFactorEnabled ?? false);
      setSessionTimeoutEnabled((orgSettings.sessionTimeoutMinutes ?? 30) > 0);
      setAuditTrailEnabled(orgSettings.auditTrailEnabled ?? true);
      setIpWhitelistEnabled(orgSettings.ipWhitelistEnabled ?? false);
    }
  }, [orgSettings]);

  const mutation = useMutation({
    mutationFn: (data: UpdateOrgSettings) => orgService.updateSettings(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["org", "settings"] });
      toast({ title: "Settings saved", description: "Organization settings updated successfully." });
    },
    onError: (err: unknown) => {
      toast({
        title: "Failed to save settings",
        description: err instanceof Error ? err.message : "Unknown error",
        variant: "destructive",
      });
    },
  });

  const handleSave = () => {
    mutation.mutate({ orgName });
  };

  const handleSaveSecurity = () => {
    mutation.mutate({
      twoFactorEnabled,
      sessionTimeoutMinutes: sessionTimeoutEnabled ? 30 : 0,
      auditTrailEnabled,
      ipWhitelistEnabled,
    });
    setSecurityDirty(false);
  };

  // Health check for integrations tab
  const checkHealth = async () => {
    setHealthResults(serviceChecks.map((s) => ({ key: s.key, status: "LOADING" })));

    const results = await Promise.all(
      serviceChecks.map(async (svc) => {
        try {
          const res = await fetch(`${svc.proxy}/actuator/health`, {
            signal: AbortSignal.timeout(5000),
          });
          if (res.ok) {
            return { key: svc.key, status: "UP" as const };
          }
          return { key: svc.key, status: "DOWN" as const };
        } catch {
          return { key: svc.key, status: "DOWN" as const };
        }
      })
    );
    setHealthResults(results);
  };

  return (
    <DashboardLayout title="Settings" subtitle="System configuration & administration">
      <div className="space-y-4 animate-fade-in max-w-4xl">
        <Tabs defaultValue="general" className="w-full">
          <TabsList className="mb-4">
            <TabsTrigger value="general" className="text-xs">General</TabsTrigger>
            <TabsTrigger value="security" className="text-xs">Security</TabsTrigger>
            <TabsTrigger value="notifications" className="text-xs">Notifications</TabsTrigger>
            <TabsTrigger value="integrations" className="text-xs" onClick={checkHealth}>Integrations</TabsTrigger>
          </TabsList>

          <TabsContent value="general">
            <Card>
              <CardHeader>
                <CardTitle className="text-sm font-medium flex items-center gap-2">
                  <Building2 className="h-4 w-4" /> Organization Settings
                </CardTitle>
              </CardHeader>
              <CardContent className="space-y-4">
                {isLoading ? (
                  <div className="flex items-center gap-2 text-muted-foreground text-sm py-4">
                    <Loader2 className="h-4 w-4 animate-spin" />
                    Loading organization settings...
                  </div>
                ) : (
                  <>
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                      <div className="space-y-1.5">
                        <Label className="text-xs">Organization Name</Label>
                        <Input
                          value={orgName}
                          onChange={(e) => setOrgName(e.target.value)}
                          className="text-sm"
                          placeholder="Enter organization name"
                        />
                      </div>
                      <div className="space-y-1.5">
                        <Label className="text-xs">Timezone</Label>
                        <Input
                          value={orgSettings?.timezone ?? ""}
                          readOnly
                          className="text-sm bg-muted/50 cursor-default"
                        />
                      </div>
                    </div>

                    <div className="space-y-1.5">
                      <Label className="text-xs">Currency</Label>
                      <div className="flex items-center gap-2">
                        <Badge variant="outline" className="flex items-center gap-1.5 px-3 py-1.5 text-sm font-mono">
                          <Lock className="h-3 w-3 text-muted-foreground" />
                          {orgSettings?.currency ?? "\u2014"}
                        </Badge>
                        <p className="text-xs text-muted-foreground">
                          Single currency enforced per organization. Contact support to change.
                        </p>
                      </div>
                    </div>

                    <Separator />

                    <div className="space-y-3">
                      <div className="flex items-center justify-between">
                        <div>
                          <p className="text-sm font-medium">Auto End-of-Day Processing</p>
                          <p className="text-xs text-muted-foreground">Automatically run EOD batch at midnight</p>
                        </div>
                        <Switch defaultChecked />
                      </div>
                    </div>

                    <div className="flex justify-end">
                      <Button
                        size="sm"
                        className="text-xs"
                        onClick={handleSave}
                        disabled={mutation.isPending}
                      >
                        {mutation.isPending && <Loader2 className="h-3.5 w-3.5 animate-spin mr-1.5" />}
                        Save Changes
                      </Button>
                    </div>
                  </>
                )}
              </CardContent>
            </Card>
          </TabsContent>

          <TabsContent value="security">
            <Card>
              <CardHeader>
                <CardTitle className="text-sm font-medium flex items-center gap-2">
                  <Shield className="h-4 w-4" /> Security & Compliance
                </CardTitle>
              </CardHeader>
              <CardContent className="space-y-3">
                {isLoading ? (
                  <div className="flex items-center gap-2 text-muted-foreground text-sm py-4">
                    <Loader2 className="h-4 w-4 animate-spin" />
                    Loading security settings...
                  </div>
                ) : (
                  <>
                    <div className="flex items-center justify-between">
                      <div>
                        <p className="text-sm font-medium">Two-Factor Authentication</p>
                        <p className="text-xs text-muted-foreground">Require 2FA for all admin users</p>
                      </div>
                      <Switch
                        checked={twoFactorEnabled}
                        onCheckedChange={(v) => { setTwoFactorEnabled(v); setSecurityDirty(true); }}
                      />
                    </div>
                    <div className="flex items-center justify-between">
                      <div>
                        <p className="text-sm font-medium">Session Timeout</p>
                        <p className="text-xs text-muted-foreground">Auto-logout after 30 minutes of inactivity</p>
                      </div>
                      <Switch
                        checked={sessionTimeoutEnabled}
                        onCheckedChange={(v) => { setSessionTimeoutEnabled(v); setSecurityDirty(true); }}
                      />
                    </div>
                    <div className="flex items-center justify-between">
                      <div>
                        <p className="text-sm font-medium">Audit Trail</p>
                        <p className="text-xs text-muted-foreground">Log all user actions for compliance</p>
                      </div>
                      <Switch
                        checked={auditTrailEnabled}
                        onCheckedChange={(v) => { setAuditTrailEnabled(v); setSecurityDirty(true); }}
                      />
                    </div>
                    <div className="flex items-center justify-between">
                      <div>
                        <p className="text-sm font-medium">IP Whitelisting</p>
                        <p className="text-xs text-muted-foreground">Restrict access to approved IP ranges</p>
                      </div>
                      <Switch
                        checked={ipWhitelistEnabled}
                        onCheckedChange={(v) => { setIpWhitelistEnabled(v); setSecurityDirty(true); }}
                      />
                    </div>

                    <Separator />

                    <div className="flex justify-end">
                      <Button
                        size="sm"
                        className="text-xs"
                        onClick={handleSaveSecurity}
                        disabled={mutation.isPending || !securityDirty}
                      >
                        {mutation.isPending && <Loader2 className="h-3.5 w-3.5 animate-spin mr-1.5" />}
                        Save Security Settings
                      </Button>
                    </div>
                  </>
                )}
              </CardContent>
            </Card>
          </TabsContent>

          <TabsContent value="notifications">
            <Card>
              <CardHeader>
                <CardTitle className="text-sm font-medium flex items-center gap-2">
                  <Bell className="h-4 w-4" /> Notification Channels
                </CardTitle>
              </CardHeader>
              <CardContent className="space-y-3">
                <div className="flex items-center justify-between py-2">
                  <div>
                    <p className="text-sm font-medium">Email Notifications</p>
                    <p className="text-xs text-muted-foreground">
                      {emailConfig ? "Email channel configured via notification service" : "Email channel not configured"}
                    </p>
                  </div>
                  <div className="flex items-center gap-2">
                    {emailConfig ? (
                      <Badge className="bg-success/15 text-success border-success/30 text-xs">Configured</Badge>
                    ) : (
                      <Badge variant="outline" className="text-xs">Not configured</Badge>
                    )}
                  </div>
                </div>
                <div className="flex items-center justify-between py-2">
                  <div>
                    <p className="text-sm font-medium">SMS Alerts</p>
                    <p className="text-xs text-muted-foreground">
                      {smsConfig ? "SMS channel configured via notification service" : "SMS channel not configured"}
                    </p>
                  </div>
                  <div className="flex items-center gap-2">
                    {smsConfig ? (
                      <Badge className="bg-success/15 text-success border-success/30 text-xs">Configured</Badge>
                    ) : (
                      <Badge variant="outline" className="text-xs">Not configured</Badge>
                    )}
                  </div>
                </div>

                <Separator />

                <div className="space-y-3">
                  <p className="text-xs text-muted-foreground">
                    Notification templates and delivery settings can be managed from the dedicated Notifications page.
                  </p>
                  <Button variant="outline" size="sm" className="text-xs" asChild>
                    <a href="/notifications">
                      Go to Notifications <ExternalLink className="h-3 w-3 ml-1.5" />
                    </a>
                  </Button>
                </div>
              </CardContent>
            </Card>
          </TabsContent>

          <TabsContent value="integrations">
            <Card>
              <CardHeader>
                <CardTitle className="text-sm font-medium flex items-center gap-2">
                  <Globe className="h-4 w-4" /> Service Health & Integrations
                </CardTitle>
              </CardHeader>
              <CardContent className="space-y-1">
                {serviceChecks.map((svc) => {
                  const health = healthResults.find((h) => h.key === svc.key);
                  const status = health?.status ?? "LOADING";
                  return (
                    <div key={svc.key} className="flex items-center justify-between py-2.5 border-b border-border/50 last:border-0">
                      <div>
                        <p className="text-sm font-medium">{svc.label}</p>
                        <p className="text-xs text-muted-foreground">{svc.desc}</p>
                      </div>
                      <div className="flex items-center gap-1.5">
                        {status === "LOADING" ? (
                          <Loader2 className="h-3.5 w-3.5 animate-spin text-muted-foreground" />
                        ) : status === "UP" ? (
                          <>
                            <CheckCircle2 className="h-3.5 w-3.5 text-success" />
                            <span className="text-xs font-medium text-success">UP</span>
                          </>
                        ) : (
                          <>
                            <XCircle className="h-3.5 w-3.5 text-destructive" />
                            <span className="text-xs font-medium text-destructive">DOWN</span>
                          </>
                        )}
                      </div>
                    </div>
                  );
                })}

                <div className="pt-3">
                  <Button variant="outline" size="sm" className="text-xs" onClick={checkHealth}>
                    Refresh Health Status
                  </Button>
                </div>
              </CardContent>
            </Card>
          </TabsContent>
        </Tabs>
      </div>
    </DashboardLayout>
  );
};

export default SettingsPage;
