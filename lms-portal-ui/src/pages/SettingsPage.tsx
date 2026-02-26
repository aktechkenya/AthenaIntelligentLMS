import { useState, useEffect } from "react";
import { useQuery, useMutation } from "@tanstack/react-query";
import { DashboardLayout } from "@/components/DashboardLayout";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Separator } from "@/components/ui/separator";
import { Switch } from "@/components/ui/switch";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Building2, Shield, Bell, Globe, Lock, Loader2 } from "lucide-react";
import { orgService, type UpdateOrgSettings } from "@/services/orgService";
import { toast } from "@/hooks/use-toast";

const SettingsPage = () => {
  const [orgName, setOrgName] = useState("");

  const { data: orgSettings, isLoading } = useQuery({
    queryKey: ["org", "settings"],
    queryFn: orgService.getSettings,
  });

  // Populate controlled inputs once data arrives
  useEffect(() => {
    if (orgSettings) {
      setOrgName(orgSettings.orgName ?? "");
    }
  }, [orgSettings]);

  const mutation = useMutation({
    mutationFn: (data: UpdateOrgSettings) => orgService.updateSettings(data),
    onSuccess: () => {
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

  return (
    <DashboardLayout title="Settings" subtitle="System configuration & administration">
      <div className="space-y-4 animate-fade-in max-w-4xl">
        <Tabs defaultValue="general" className="w-full">
          <TabsList className="mb-4">
            <TabsTrigger value="general" className="text-xs">General</TabsTrigger>
            <TabsTrigger value="security" className="text-xs">Security</TabsTrigger>
            <TabsTrigger value="notifications" className="text-xs">Notifications</TabsTrigger>
            <TabsTrigger value="integrations" className="text-xs">Integrations</TabsTrigger>
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
                    Loading organization settings…
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
                          {orgSettings?.currency ?? "—"}
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
                <div className="flex items-center justify-between">
                  <div>
                    <p className="text-sm font-medium">Two-Factor Authentication</p>
                    <p className="text-xs text-muted-foreground">Require 2FA for all admin users</p>
                  </div>
                  <Switch defaultChecked />
                </div>
                <div className="flex items-center justify-between">
                  <div>
                    <p className="text-sm font-medium">Session Timeout</p>
                    <p className="text-xs text-muted-foreground">Auto-logout after 30 minutes of inactivity</p>
                  </div>
                  <Switch defaultChecked />
                </div>
                <div className="flex items-center justify-between">
                  <div>
                    <p className="text-sm font-medium">Audit Trail</p>
                    <p className="text-xs text-muted-foreground">Log all user actions for compliance</p>
                  </div>
                  <Switch defaultChecked />
                </div>
                <div className="flex items-center justify-between">
                  <div>
                    <p className="text-sm font-medium">IP Whitelisting</p>
                    <p className="text-xs text-muted-foreground">Restrict access to approved IP ranges</p>
                  </div>
                  <Switch />
                </div>
              </CardContent>
            </Card>
          </TabsContent>

          <TabsContent value="notifications">
            <Card>
              <CardHeader>
                <CardTitle className="text-sm font-medium flex items-center gap-2">
                  <Bell className="h-4 w-4" /> Notification Preferences
                </CardTitle>
              </CardHeader>
              <CardContent className="space-y-3">
                <div className="flex items-center justify-between">
                  <div>
                    <p className="text-sm font-medium">Loan Approval Alerts</p>
                    <p className="text-xs text-muted-foreground">Notify when loans require approval</p>
                  </div>
                  <Switch defaultChecked />
                </div>
                <div className="flex items-center justify-between">
                  <div>
                    <p className="text-sm font-medium">Overdue Notifications</p>
                    <p className="text-xs text-muted-foreground">Alert on past-due accounts</p>
                  </div>
                  <Switch defaultChecked />
                </div>
                <div className="flex items-center justify-between">
                  <div>
                    <p className="text-sm font-medium">System Health Alerts</p>
                    <p className="text-xs text-muted-foreground">EOD failures, API errors, etc.</p>
                  </div>
                  <Switch defaultChecked />
                </div>
              </CardContent>
            </Card>
          </TabsContent>

          <TabsContent value="integrations">
            <Card>
              <CardHeader>
                <CardTitle className="text-sm font-medium flex items-center gap-2">
                  <Globe className="h-4 w-4" /> API & Integrations
                </CardTitle>
              </CardHeader>
              <CardContent className="space-y-4">
                {[
                  { name: "Credit Bureau", status: "Connected", desc: "TransUnion / Metropol" },
                  { name: "Payment Gateway", status: "Connected", desc: "M-Pesa, Bank Transfer" },
                  { name: "SMS Provider", status: "Connected", desc: "Africa's Talking" },
                  { name: "Email Service", status: "Not configured", desc: "SendGrid / SES" },
                ].map((item) => (
                  <div key={item.name} className="flex items-center justify-between py-2 border-b border-border/50 last:border-0">
                    <div>
                      <p className="text-sm font-medium">{item.name}</p>
                      <p className="text-xs text-muted-foreground">{item.desc}</p>
                    </div>
                    <span className={`text-xs font-medium ${item.status === "Connected" ? "text-success" : "text-muted-foreground"}`}>
                      {item.status}
                    </span>
                  </div>
                ))}
              </CardContent>
            </Card>
          </TabsContent>
        </Tabs>
      </div>
    </DashboardLayout>
  );
};

export default SettingsPage;
